package com.trustlens.riskengine

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.Confidence
import com.trustlens.domain.ExternalEvidence
import com.trustlens.domain.InferenceBackend
import com.trustlens.domain.Indicators
import com.trustlens.domain.InvestigationContext
import com.trustlens.domain.InvestigationSource
import com.trustlens.domain.MlSignal
import com.trustlens.domain.Observation
import com.trustlens.domain.ObservationKind
import com.trustlens.domain.ReputationVerdict
import com.trustlens.domain.RiskLevel
import com.trustlens.domain.SafeAction
import com.trustlens.domain.ScamCategory
import org.junit.Test

class RiskEngineTest {

    private val engine = RiskEngine()

    private fun context(
        source: InvestigationSource = InvestigationSource.SMS,
        rawContent: String = "hello",
        indicators: Indicators = Indicators(),
        mlSignal: MlSignal? = null,
        observations: List<Observation> = emptyList(),
        externalEvidence: List<ExternalEvidence> = emptyList(),
        threatIntelUnavailable: Boolean = false,
    ) = InvestigationContext(
        source = source,
        rawContent = rawContent,
        indicators = indicators,
        mlSignal = mlSignal,
        observations = observations,
        externalEvidence = externalEvidence,
        threatIntelUnavailable = threatIntelUnavailable,
    )

    private fun ml(probability: Double) = MlSignal(
        modelVersion = "test-1.0",
        scamProbability = probability,
        category = ScamCategory.FINANCIAL_PHISHING,
        confidence = 0.9,
        backend = InferenceBackend.MOCK,
    )

    // --- 1. Zero / minimal risk -------------------------------------------------------

    @Test
    fun `benign content with no indicators scores zero and is LOW`() {
        val result = engine.assess(context(rawContent = "Lunch at 1pm?"))

        assertThat(result.score).isEqualTo(0)
        assertThat(result.level).isEqualTo(RiskLevel.LOW)
        assertThat(result.observations).isEmpty()
    }

    @Test
    fun `a single weak indicator stays in the lower bands`() {
        val result = engine.assess(context(indicators = Indicators(urgency = true)))

        assertThat(result.score).isGreaterThan(0)
        assertThat(result.level).isAnyOf(RiskLevel.LOW, RiskLevel.GUARDED)
    }

    // --- 2. Multiple risk indicators --------------------------------------------------

    @Test
    fun `more indicators produce a strictly higher score than fewer`() {
        val few = engine.assess(context(indicators = Indicators(urgency = true)))
        val many = engine.assess(
            context(
                indicators = Indicators(
                    urgency = true,
                    otpRequest = true,
                    paymentRequest = true,
                    accountThreat = true,
                ),
            ),
        )

        assertThat(many.score).isGreaterThan(few.score)
    }

    @Test
    fun `a full financial phishing pattern reaches CRITICAL`() {
        val result = engine.assess(
            context(
                rawContent = "Your SBI account will be blocked. Share OTP and pay now.",
                indicators = Indicators(
                    urls = listOf("http://sbi-verify.example.com"),
                    urgency = true,
                    otpRequest = true,
                    paymentRequest = true,
                    credentialRequest = true,
                    accountThreat = true,
                    impersonatedOrganisation = "SBI",
                ),
                mlSignal = ml(0.97),
                externalEvidence = listOf(
                    ExternalEvidence(
                        sourceName = "TestIntel",
                        verdict = ReputationVerdict.CONFIRMED,
                        subject = "sbi-verify.example.com",
                        summary = "Reported malicious",
                    ),
                ),
            ),
        )

        assertThat(result.level).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.recommendedActions).contains(SafeAction.DO_NOT_SHARE_OTP)
        assertThat(result.recommendedActions).contains(SafeAction.DO_NOT_PAY)
    }

    // --- 3. Risk score calculation ----------------------------------------------------

    @Test
    fun `score is the exact sum of the weights of the contributing signals`() {
        // urgency (10) + otpRequest (22) + ML 0.5 * 30 (15) = 47
        // OTP_REQUEST moved 20 -> 22 in the Phase 11 calibration; the property under test
        // — that the score is exactly the sum of the contributing weights — is unchanged.
        val result = engine.assess(
            context(
                indicators = Indicators(urgency = true, otpRequest = true),
                mlSignal = ml(0.5),
            ),
        )

        val expected = RiskWeights.URGENCY +
            RiskWeights.OTP_REQUEST +
            (0.5 * RiskWeights.ML_SIGNAL_MAX).toInt()

        assertThat(result.score).isEqualTo(expected)
        assertThat(result.score).isEqualTo(47)
    }

    @Test
    fun `every scored signal is explained in the reasons`() {
        val result = engine.assess(
            context(indicators = Indicators(urgency = true, otpRequest = true)),
        )

        assertThat(result.reasons).hasSize(result.observations.size)
        assertThat(result.reasons).isNotEmpty()
    }

    // --- 4. 0-100 capping -------------------------------------------------------------

    @Test
    fun `score is capped at 100 when signals would otherwise exceed it`() {
        val everything = Indicators(
            urls = listOf("http://1.2.3.4/verify"),
            urgency = true,
            otpRequest = true,
            paymentRequest = true,
            credentialRequest = true,
            accountThreat = true,
            impersonatedOrganisation = "SBI",
        )
        val allObservations = ObservationKind.entries.map {
            Observation(kind = it, summary = it.name)
        }
        val result = engine.assess(
            context(
                indicators = everything,
                mlSignal = ml(1.0),
                observations = allObservations,
                externalEvidence = listOf(
                    ExternalEvidence("A", ReputationVerdict.CONFIRMED, "x", "malicious"),
                    ExternalEvidence("B", ReputationVerdict.CONFIRMED, "y", "malicious"),
                    ExternalEvidence("C", ReputationVerdict.CONFIRMED, "z", "malicious"),
                ),
            ),
        )

        assertThat(result.score).isEqualTo(100)
        assertThat(result.level).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `score never falls below zero`() {
        val result = engine.assess(
            context(
                externalEvidence = listOf(
                    ExternalEvidence("A", ReputationVerdict.NO_DETECTION, "x", "no report"),
                    ExternalEvidence("B", ReputationVerdict.NO_DETECTION, "y", "no report"),
                ),
            ),
        )

        assertThat(result.score).isAtLeast(0)
    }

    // --- 5. Risk level boundaries -----------------------------------------------------

    @Test
    fun `risk level boundaries map exactly to the specified bands`() {
        assertThat(RiskLevel.forScore(0)).isEqualTo(RiskLevel.LOW)
        assertThat(RiskLevel.forScore(19)).isEqualTo(RiskLevel.LOW)
        assertThat(RiskLevel.forScore(20)).isEqualTo(RiskLevel.GUARDED)
        assertThat(RiskLevel.forScore(39)).isEqualTo(RiskLevel.GUARDED)
        assertThat(RiskLevel.forScore(40)).isEqualTo(RiskLevel.MEDIUM)
        assertThat(RiskLevel.forScore(60)).isEqualTo(RiskLevel.MEDIUM)
        assertThat(RiskLevel.forScore(61)).isEqualTo(RiskLevel.HIGH)
        assertThat(RiskLevel.forScore(80)).isEqualTo(RiskLevel.HIGH)
        assertThat(RiskLevel.forScore(81)).isEqualTo(RiskLevel.CRITICAL)
        assertThat(RiskLevel.forScore(100)).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `every score in the valid range maps to exactly one level`() {
        (0..100).forEach { score ->
            val matches = RiskLevel.entries.filter { score in it.range }
            assertThat(matches).hasSize(1)
        }
    }

    // --- 6. UNKNOWN is never SAFE -----------------------------------------------------

    @Test
    fun `an UNKNOWN reputation verdict does not reduce the score`() {
        val indicators = Indicators(urgency = true, otpRequest = true)
        val withoutLookup = engine.assess(context(indicators = indicators))
        val withUnknown = engine.assess(
            context(
                indicators = indicators,
                externalEvidence = listOf(
                    ExternalEvidence("VT", ReputationVerdict.UNKNOWN, "x", "no data"),
                ),
            ),
        )

        assertThat(withUnknown.score).isEqualTo(withoutLookup.score)
    }

    @Test
    fun `a NO_DETECTION verdict does not reduce the score`() {
        val indicators = Indicators(urgency = true, otpRequest = true)
        val withoutLookup = engine.assess(context(indicators = indicators))
        val withNoDetection = engine.assess(
            context(
                indicators = indicators,
                externalEvidence = listOf(
                    ExternalEvidence("VT", ReputationVerdict.NO_DETECTION, "x", "no report"),
                ),
            ),
        )

        assertThat(withNoDetection.score).isEqualTo(withoutLookup.score)
    }

    @Test
    fun `dangerous content stays dangerous when every lookup is UNKNOWN`() {
        val result = engine.assess(
            context(
                indicators = Indicators(
                    urgency = true,
                    otpRequest = true,
                    credentialRequest = true,
                    accountThreat = true,
                    impersonatedOrganisation = "SBI",
                ),
                mlSignal = ml(0.95),
                externalEvidence = listOf(
                    ExternalEvidence("VT", ReputationVerdict.UNKNOWN, "x", "no data"),
                    ExternalEvidence("URLhaus", ReputationVerdict.ERROR, "x", "timeout"),
                ),
            ),
        )

        assertThat(result.level).isAnyOf(RiskLevel.HIGH, RiskLevel.CRITICAL)
    }

    @Test
    fun `confidence is never HIGH when external evidence is unusable`() {
        val result = engine.assess(
            context(
                indicators = Indicators(urgency = true, otpRequest = true),
                mlSignal = ml(0.9),
                externalEvidence = listOf(
                    ExternalEvidence("VT", ReputationVerdict.ERROR, "x", "rate limited"),
                ),
            ),
        )

        assertThat(result.confidence).isNotEqualTo(Confidence.HIGH)
    }

    @Test
    fun `unavailable threat intelligence marks the result degraded`() {
        val result = engine.assess(
            context(
                indicators = Indicators(urgency = true),
                threatIntelUnavailable = true,
            ),
        )

        assertThat(result.degraded).isTrue()
    }

    @Test
    fun `a clean result is not presented as verified safe`() {
        val result = engine.assess(context(rawContent = "Lunch at 1pm?"))

        // Lowest band still advises care; nothing in the vocabulary asserts safety.
        assertThat(result.recommendedActions).contains(SafeAction.PROCEED_CAREFULLY)
        assertThat(result.confidence).isNotEqualTo(Confidence.HIGH)
    }

    // --- 7. Evidence vs inference separation ------------------------------------------

    @Test
    fun `inferences are produced as output and are never scored as input`() {
        val result = engine.assess(
            context(
                indicators = Indicators(urgency = true, otpRequest = true),
                mlSignal = ml(0.9),
            ),
        )

        assertThat(result.inferences).isNotEmpty()
        // Reasons account for scored signals only, so they cannot outnumber the inputs.
        assertThat(result.reasons).hasSize(result.observations.size + result.externalEvidence.size)
    }

    @Test
    fun `observations and external evidence are kept in separate collections`() {
        val evidence = ExternalEvidence("VT", ReputationVerdict.CONFIRMED, "x", "malicious")
        val result = engine.assess(
            context(
                indicators = Indicators(urgency = true),
                externalEvidence = listOf(evidence),
            ),
        )

        assertThat(result.externalEvidence).containsExactly(evidence)
        assertThat(result.observations.map { it.kind }).containsExactly(ObservationKind.URGENCY)
    }

    @Test
    fun `an external CONFIRMED verdict raises the score above local signals alone`() {
        val local = engine.assess(context(indicators = Indicators(urgency = true)))
        val withEvidence = engine.assess(
            context(
                indicators = Indicators(urgency = true),
                externalEvidence = listOf(
                    ExternalEvidence("VT", ReputationVerdict.CONFIRMED, "x", "malicious"),
                ),
            ),
        )

        assertThat(withEvidence.score).isGreaterThan(local.score)
    }

    // --- 8. Determinism ---------------------------------------------------------------

    @Test
    fun `the same input always produces an identical result`() {
        val ctx = context(
            indicators = Indicators(
                urgency = true,
                otpRequest = true,
                impersonatedOrganisation = "SBI",
            ),
            mlSignal = ml(0.83),
            externalEvidence = listOf(
                ExternalEvidence("VT", ReputationVerdict.SUSPICIOUS, "x", "suspicious"),
            ),
        )

        val results = (1..50).map { engine.assess(ctx) }

        assertThat(results.distinct()).hasSize(1)
    }

    @Test
    fun `a fresh engine instance produces the same result as another`() {
        val ctx = context(
            indicators = Indicators(urgency = true, paymentRequest = true),
            mlSignal = ml(0.6),
        )

        assertThat(RiskEngine().assess(ctx)).isEqualTo(RiskEngine().assess(ctx))
    }

    @Test
    fun `indicator ordering does not change the score`() {
        val a = engine.assess(
            context(
                observations = listOf(
                    Observation(ObservationKind.URGENCY, "urgent"),
                    Observation(ObservationKind.OTP_REQUEST, "otp"),
                ),
            ),
        )
        val b = engine.assess(
            context(
                observations = listOf(
                    Observation(ObservationKind.OTP_REQUEST, "otp"),
                    Observation(ObservationKind.URGENCY, "urgent"),
                ),
            ),
        )

        assertThat(a.score).isEqualTo(b.score)
    }
}
