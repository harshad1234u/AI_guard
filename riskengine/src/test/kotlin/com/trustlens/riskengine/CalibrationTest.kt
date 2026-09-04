package com.trustlens.riskengine

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.Confidence
import com.trustlens.domain.ExternalEvidence
import com.trustlens.domain.Indicators
import com.trustlens.domain.InferenceBackend
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

/**
 * The calibration specification.
 *
 * These tests deliberately assert **bands and orderings**, not magic numbers. Pinning exact
 * scores would make the suite a record of whatever the weights happen to be, and would let a
 * single hero message be tuned into looking right. What matters is that more independent
 * evidence produces more risk, that absence of evidence produces none, and that each
 * scenario lands where a security analyst would put it.
 *
 * The exact numbers the calibrated engine produces are recorded in the phase report.
 */
class CalibrationTest {

    private val engine = RiskEngine()

    private fun context(
        indicators: Indicators = Indicators(),
        observations: List<Observation> = emptyList(),
        evidence: List<ExternalEvidence> = emptyList(),
        mlProbability: Double? = null,
        threatIntelUnavailable: Boolean = true,
    ) = InvestigationContext(
        source = InvestigationSource.TEXT,
        rawContent = "content",
        indicators = indicators,
        mlSignal = mlProbability?.let {
            MlSignal(
                modelVersion = "test",
                scamProbability = it,
                category = ScamCategory.UNKNOWN,
                confidence = 0.5,
                backend = InferenceBackend.CPU,
            )
        },
        observations = observations,
        externalEvidence = evidence,
        threatIntelUnavailable = threatIntelUnavailable,
    )

    private fun observation(kind: ObservationKind) =
        Observation(kind, summary = kind.name, detail = "https://example.com")

    private fun evidence(
        source: String,
        verdict: ReputationVerdict,
        subject: String = "example.com",
    ) = ExternalEvidence(source, verdict, subject, "$source says $verdict")

    private fun score(
        indicators: Indicators = Indicators(),
        observations: List<Observation> = emptyList(),
        evidence: List<ExternalEvidence> = emptyList(),
        mlProbability: Double? = null,
    ) = engine.assess(context(indicators, observations, evidence, mlProbability)).score

    // ---- the primary defect ---------------------------------------------------------------

    @Test
    fun `each independent url structural property is scored separately`() {
        val deep = score(observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)))
        val deepAndExecutable = score(
            observations = listOf(
                observation(ObservationKind.DEEP_SUBDOMAIN),
                observation(ObservationKind.EXECUTABLE_DOWNLOAD_PATH),
            ),
        )
        val allThree = score(
            observations = listOf(
                observation(ObservationKind.DEEP_SUBDOMAIN),
                observation(ObservationKind.EXECUTABLE_DOWNLOAD_PATH),
                observation(ObservationKind.NON_STANDARD_PORT),
            ),
        )

        // The defect this phase exists to fix: these used to be one +10 regardless.
        assertThat(deep).isLessThan(deepAndExecutable)
        assertThat(deepAndExecutable).isLessThan(allThree)
    }

    @Test
    fun `the hero suspicious url lands meaningfully above low`() {
        // https://sbi.secure.login.example.com:8443/kyc/update.apk
        val result = engine.assess(
            context(
                indicators = Indicators(urls = listOf("https://x")),
                observations = listOf(
                    observation(ObservationKind.DEEP_SUBDOMAIN),
                    observation(ObservationKind.EXECUTABLE_DOWNLOAD_PATH),
                    observation(ObservationKind.NON_STANDARD_PORT),
                ),
            ),
        )

        assertThat(result.level).isAtLeast(RiskLevel.GUARDED)
        assertThat(result.recommendedActions).contains(SafeAction.DO_NOT_OPEN_LINK)
    }

    @Test
    fun `the same structural property seen on two urls is still scored once`() {
        val once = score(observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)))
        val twice = score(
            observations = listOf(
                observation(ObservationKind.DEEP_SUBDOMAIN),
                observation(ObservationKind.DEEP_SUBDOMAIN),
            ),
        )

        assertThat(twice).isEqualTo(once)
    }

    // ---- provider corroboration ------------------------------------------------------------

    @Test
    fun `two providers confirming outweigh one, without doubling`() {
        val one = score(evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED)))
        val two = score(
            evidence = listOf(
                evidence("VirusTotal", ReputationVerdict.CONFIRMED),
                evidence("URLhaus", ReputationVerdict.CONFIRMED),
            ),
        )

        assertThat(two).isGreaterThan(one)
        assertThat(two).isLessThan(one * 2)
    }

    @Test
    fun `one provider confirming still matters on its own`() {
        val result = engine.assess(
            context(evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED))),
        )

        // A single strong provider result must not be ignored for lack of a second opinion.
        assertThat(result.level).isAtLeast(RiskLevel.MEDIUM)
    }

    @Test
    fun `confirmed outweighs suspicious which outweighs no detection`() {
        val confirmed = score(evidence = listOf(evidence("A", ReputationVerdict.CONFIRMED)))
        val suspicious = score(evidence = listOf(evidence("A", ReputationVerdict.SUSPICIOUS)))
        val none = score(evidence = listOf(evidence("A", ReputationVerdict.NO_DETECTION)))

        assertThat(confirmed).isGreaterThan(suspicious)
        assertThat(suspicious).isGreaterThan(none)
        assertThat(none).isEqualTo(0)
    }

    @Test
    fun `the same provider reporting twice does not corroborate itself`() {
        val once = score(evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED)))
        val twiceSameSource = score(
            evidence = listOf(
                evidence("VirusTotal", ReputationVerdict.CONFIRMED, "a.example.com"),
                evidence("VirusTotal", ReputationVerdict.CONFIRMED, "b.example.com"),
            ),
        )

        // Corroboration means *independent* sources. One provider is one opinion.
        assertThat(twiceSameSource).isEqualTo(once)
    }

    // ---- UNKNOWN is never SAFE ---------------------------------------------------------------

    @Test
    fun `no detection, unknown and error never reduce a score`() {
        val base = score(
            indicators = Indicators(otpRequest = true),
            observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)),
        )

        listOf(
            ReputationVerdict.NO_DETECTION,
            ReputationVerdict.UNKNOWN,
            ReputationVerdict.ERROR,
        ).forEach { verdict ->
            val withVerdict = score(
                indicators = Indicators(otpRequest = true),
                observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)),
                evidence = listOf(evidence("A", verdict), evidence("B", verdict)),
            )

            assertThat(withVerdict).isEqualTo(base)
        }
    }

    // ---- calibration matrix ---------------------------------------------------------------------

    @Test
    fun `A - a benign message is low`() {
        assertThat(engine.assess(context()).level).isEqualTo(RiskLevel.LOW)
    }

    @Test
    fun `C - bank impersonation with a mismatched link is high or critical`() {
        val result = engine.assess(
            context(
                indicators = Indicators(
                    urls = listOf("https://x"),
                    organisations = listOf("SBI"),
                    impersonatedOrganisation = "SBI",
                    urgency = true,
                    otpRequest = true,
                    accountThreat = true,
                ),
                observations = listOf(
                    observation(ObservationKind.DOMAIN_ORGANISATION_MISMATCH),
                ),
                mlProbability = 0.7,
            ),
        )

        assertThat(result.level).isAtLeast(RiskLevel.HIGH)
    }

    @Test
    fun `D - urgency plus otp request plus account threat is high or critical`() {
        val result = engine.assess(
            context(
                indicators = Indicators(
                    urgency = true,
                    otpRequest = true,
                    accountThreat = true,
                ),
                mlProbability = 0.7,
            ),
        )

        assertThat(result.level).isAtLeast(RiskLevel.HIGH)
        assertThat(result.recommendedActions).contains(SafeAction.DO_NOT_SHARE_OTP)
    }

    @Test
    fun `E - urgency plus payment request plus suspicious destination is elevated`() {
        val result = engine.assess(
            context(
                indicators = Indicators(
                    urgency = true,
                    paymentRequest = true,
                    upiIds = listOf("attacker@ybl"),
                ),
                observations = listOf(observation(ObservationKind.UPI_PAYMENT_REQUEST)),
                mlProbability = 0.6,
            ),
        )

        assertThat(result.level).isAtLeast(RiskLevel.MEDIUM)
        assertThat(result.recommendedActions).contains(SafeAction.DO_NOT_PAY)
    }

    @Test
    fun `F - local suspicion plus a confirmed provider is significantly elevated`() {
        val localOnly = engine.assess(
            context(
                indicators = Indicators(urls = listOf("https://x")),
                observations = listOf(
                    observation(ObservationKind.DEEP_SUBDOMAIN),
                    observation(ObservationKind.EXECUTABLE_DOWNLOAD_PATH),
                    observation(ObservationKind.NON_STANDARD_PORT),
                ),
            ),
        )
        val withProvider = engine.assess(
            context(
                indicators = Indicators(urls = listOf("https://x")),
                observations = listOf(
                    observation(ObservationKind.DEEP_SUBDOMAIN),
                    observation(ObservationKind.EXECUTABLE_DOWNLOAD_PATH),
                    observation(ObservationKind.NON_STANDARD_PORT),
                ),
                evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED)),
                threatIntelUnavailable = false,
            ),
        )

        assertThat(withProvider.level).isAtLeast(RiskLevel.HIGH)
        assertThat(withProvider.score).isGreaterThan(localOnly.score)
    }

    @Test
    fun `G - a clean provider response leaves local risk exactly where it was`() {
        val observations = listOf(
            observation(ObservationKind.DEEP_SUBDOMAIN),
            observation(ObservationKind.EXECUTABLE_DOWNLOAD_PATH),
        )
        val localOnly = engine.assess(context(observations = observations))
        val withClean = engine.assess(
            context(
                observations = observations,
                evidence = listOf(
                    evidence("VirusTotal", ReputationVerdict.NO_DETECTION),
                    evidence("URLhaus", ReputationVerdict.NO_DETECTION),
                ),
                threatIntelUnavailable = false,
            ),
        )

        assertThat(withClean.score).isEqualTo(localOnly.score)
        assertThat(withClean.level).isEqualTo(localOnly.level)
    }

    @Test
    fun `H - unavailable providers leave local risk unchanged and report degraded`() {
        val observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN))
        val localOnly = engine.assess(context(observations = observations))
        val withUnknown = engine.assess(
            context(
                observations = observations,
                evidence = listOf(
                    evidence("VirusTotal", ReputationVerdict.UNKNOWN),
                    evidence("URLhaus", ReputationVerdict.UNKNOWN),
                ),
                threatIntelUnavailable = false,
            ),
        )

        assertThat(withUnknown.score).isEqualTo(localOnly.score)
        assertThat(withUnknown.degraded).isTrue()
    }

    // ---- monotonicity -------------------------------------------------------------------------------

    @Test
    fun `adding genuine evidence never lowers the score`() {
        val base = score(mlProbability = 0.5)
        val plusUrgency = score(indicators = Indicators(urgency = true), mlProbability = 0.5)
        val plusOtp = score(
            indicators = Indicators(urgency = true, otpRequest = true),
            mlProbability = 0.5,
        )
        val plusUrl = score(
            indicators = Indicators(urgency = true, otpRequest = true),
            observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)),
            mlProbability = 0.5,
        )
        val plusProvider = score(
            indicators = Indicators(urgency = true, otpRequest = true),
            observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)),
            evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED)),
            mlProbability = 0.5,
        )

        assertThat(listOf(base, plusUrgency, plusOtp, plusUrl, plusProvider))
            .isInOrder()
    }

    @Test
    fun `a stronger classifier probability never lowers the score`() {
        val scores = (0..10).map { score(mlProbability = it / 10.0) }

        assertThat(scores).isInOrder()
    }

    @Test
    fun `every observation kind contributes a non-negative amount`() {
        ObservationKind.entries.forEach { kind ->
            val with = score(observations = listOf(observation(kind)))

            assertThat(with).isAtLeast(0)
        }
    }

    // ---- properties -----------------------------------------------------------------------------------

    @Test
    fun `every score in 0 to 100 maps to exactly one band`() {
        (0..100).forEach { value ->
            val matching = RiskLevel.entries.filter { value in it.range }

            assertThat(matching).hasSize(1)
        }
    }

    @Test
    fun `the score is always within bounds even with every signal at once`() {
        val result = engine.assess(
            context(
                indicators = Indicators(
                    urls = listOf("https://x"),
                    upiIds = listOf("a@b"),
                    organisations = listOf("SBI"),
                    impersonatedOrganisation = "SBI",
                    urgency = true,
                    otpRequest = true,
                    paymentRequest = true,
                    credentialRequest = true,
                    accountThreat = true,
                ),
                observations = ObservationKind.entries.map { observation(it) },
                evidence = listOf(
                    evidence("A", ReputationVerdict.CONFIRMED),
                    evidence("B", ReputationVerdict.CONFIRMED),
                    evidence("C", ReputationVerdict.CONFIRMED),
                ),
                mlProbability = 1.0,
                threatIntelUnavailable = false,
            ),
        )

        assertThat(result.score).isIn(0..100)
        assertThat(result.level).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test
    fun `identical evidence always produces an identical result`() {
        val ctx = context(
            indicators = Indicators(urgency = true, otpRequest = true),
            observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)),
            evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED)),
            mlProbability = 0.6,
        )

        assertThat((1..25).map { engine.assess(ctx) }.distinct()).hasSize(1)
    }

    @Test
    fun `every reason shown corresponds to something that actually contributed`() {
        val result = engine.assess(
            context(
                indicators = Indicators(urgency = true, otpRequest = true),
                observations = listOf(observation(ObservationKind.DEEP_SUBDOMAIN)),
                evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED)),
                mlProbability = 0.6,
                threatIntelUnavailable = false,
            ),
        )

        // Each reason carries its own contribution, and they add up to the score.
        val stated = Regex("""\(\+(\d+)\)""").findAll(result.reasons.joinToString(" "))
            .sumOf { it.groupValues[1].toInt() }

        assertThat(stated).isEqualTo(result.score)
    }

    // ---- confidence is about agreement, not danger -----------------------------------------------------

    @Test
    fun `confidence is not merely a restatement of the score`() {
        val highRiskThinEvidence = engine.assess(
            context(
                indicators = Indicators(
                    urgency = true,
                    otpRequest = true,
                    accountThreat = true,
                    credentialRequest = true,
                ),
            ),
        )

        assertThat(highRiskThinEvidence.level).isAtLeast(RiskLevel.MEDIUM)
        // No classifier, no external corroboration — the verdict is real but thinly held.
        assertThat(highRiskThinEvidence.confidence)
            .isNotEqualTo(Confidence.HIGH)
    }

    @Test
    fun `agreeing independent providers raise confidence`() {
        val single = engine.assess(
            context(
                evidence = listOf(evidence("VirusTotal", ReputationVerdict.CONFIRMED)),
                mlProbability = 0.8,
                threatIntelUnavailable = false,
            ),
        )
        val agreeing = engine.assess(
            context(
                evidence = listOf(
                    evidence("VirusTotal", ReputationVerdict.CONFIRMED),
                    evidence("URLhaus", ReputationVerdict.CONFIRMED),
                ),
                mlProbability = 0.8,
                threatIntelUnavailable = false,
            ),
        )

        assertThat(agreeing.confidence).isAtLeast(single.confidence)
        assertThat(agreeing.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `providers that disagree lower confidence rather than raising it`() {
        val agreeing = engine.assess(
            context(
                evidence = listOf(
                    evidence("VirusTotal", ReputationVerdict.CONFIRMED),
                    evidence("URLhaus", ReputationVerdict.CONFIRMED),
                ),
                mlProbability = 0.8,
                threatIntelUnavailable = false,
            ),
        )
        val disagreeing = engine.assess(
            context(
                evidence = listOf(
                    evidence("VirusTotal", ReputationVerdict.CONFIRMED),
                    evidence("URLhaus", ReputationVerdict.NO_DETECTION),
                ),
                mlProbability = 0.8,
                threatIntelUnavailable = false,
            ),
        )

        assertThat(disagreeing.confidence).isLessThan(agreeing.confidence)
    }

    @Test
    fun `a degraded lookup can never yield high confidence`() {
        val result = engine.assess(
            context(
                evidence = listOf(
                    evidence("VirusTotal", ReputationVerdict.CONFIRMED),
                    evidence("URLhaus", ReputationVerdict.ERROR),
                ),
                mlProbability = 0.9,
                threatIntelUnavailable = false,
            ),
        )

        assertThat(result.confidence).isNotEqualTo(Confidence.HIGH)
    }
}
