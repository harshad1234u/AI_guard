package com.trustlens

import com.trustlens.ai.MockScamClassifier
import com.trustlens.domain.AnalyzeTextUseCase
import com.trustlens.domain.AnalyzeUrlUseCase
import com.trustlens.domain.ExternalEvidence
import com.trustlens.domain.InvestigateIndicatorsUseCase
import com.trustlens.domain.ReputationSource
import com.trustlens.domain.ReputationVerdict
import com.trustlens.investigation.DeterministicUrlAnalyzer
import com.trustlens.investigation.RuleBasedIndicatorExtractor
import com.trustlens.riskengine.RiskEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Prints the calibration matrix over the real pipeline.
 *
 * This is a reporting harness, not a gate: it asserts nothing, so it can never be the reason
 * a weight "has" to be a particular number. The behavioural guarantees live in
 * `CalibrationTest`, which asserts bands and orderings only. This exists so the phase report
 * can quote measured scores rather than predicted ones.
 */
class CalibrationMatrixReportTest {

    private val extractor = RuleBasedIndicatorExtractor()
    private val urlAnalyzer = DeterministicUrlAnalyzer()
    private val engine = RiskEngine()

    private class StubSource(
        override val sourceName: String,
        private val verdict: ReputationVerdict,
    ) : ReputationSource {
        override suspend fun lookup(subject: String) = ExternalEvidence(
            sourceName = sourceName,
            verdict = verdict,
            subject = subject,
            summary = "$sourceName reports $verdict for $subject",
        )
    }

    private fun textUseCase(vararg sources: ReputationSource) = AnalyzeTextUseCase(
        extractor = extractor,
        urlAnalyzer = urlAnalyzer,
        classifier = MockScamClassifier(),
        riskAssessor = engine,
        threatIntel = if (sources.isEmpty()) null else InvestigateIndicatorsUseCase(sources.toSet()),
    )

    private val hostileUrl = "https://sbi.secure.login.example.com:8443/kyc/update.apk"

    private val benign = "Hi, are we still on for lunch at 1pm tomorrow?"
    private val otpScam = "URGENT! Your account will be blocked today. Share your OTP now to keep it active."
    private val bankImpersonation =
        "URGENT: Your SBI account will be blocked today. Complete KYC at " +
            "https://sbi-kyc.example.com/verify and share your OTP, or pay Rs. 4999."
    private val paymentScam =
        "Immediately transfer Rs. 50000 to attacker@ybl or your parcel will be returned today."

    /**
     * Recomputes a result under the pre-Phase-11 weight table, from the same observations
     * and evidence, so the report can quote measured before/after pairs instead of estimates.
     *
     * Reproduces the two behaviours that were calibrated away: the four structural URL
     * properties collapsing into one SUSPICIOUS_URL worth 10, and external reports summing
     * at a flat 35 apiece.
     */
    private fun legacyScore(result: com.trustlens.domain.AnalysisResult): Int {
        val legacyObservation = mapOf(
            com.trustlens.domain.ObservationKind.OTP_REQUEST to 20,
            com.trustlens.domain.ObservationKind.CREDENTIAL_REQUEST to 18,
            com.trustlens.domain.ObservationKind.PAYMENT_REQUEST to 15,
            com.trustlens.domain.ObservationKind.ORGANISATION_IMPERSONATION to 12,
            com.trustlens.domain.ObservationKind.DOMAIN_ORGANISATION_MISMATCH to 12,
            com.trustlens.domain.ObservationKind.URGENCY to 10,
            com.trustlens.domain.ObservationKind.ACCOUNT_THREAT to 8,
            com.trustlens.domain.ObservationKind.PUNYCODE_DOMAIN to 14,
            com.trustlens.domain.ObservationKind.IP_ADDRESS_URL to 12,
            com.trustlens.domain.ObservationKind.URL_SHORTENER to 6,
            com.trustlens.domain.ObservationKind.UPI_PAYMENT_REQUEST to 10,
            com.trustlens.domain.ObservationKind.VOICE_SCAM_LANGUAGE to 8,
        )
        val merged = setOf(
            com.trustlens.domain.ObservationKind.SUSPICIOUS_URL,
            com.trustlens.domain.ObservationKind.EMBEDDED_CREDENTIALS,
            com.trustlens.domain.ObservationKind.EXECUTABLE_DOWNLOAD_PATH,
            com.trustlens.domain.ObservationKind.DEEP_SUBDOMAIN,
            com.trustlens.domain.ObservationKind.NON_STANDARD_PORT,
        )

        val ml = result.context.mlSignal?.let { (it.scamProbability * 30).toInt() } ?: 0
        val kinds = result.observations.map { it.kind }.toSet()
        val structural = if (kinds.any { it in merged }) 10 else 0
        val local = kinds.sumOf { legacyObservation[it] ?: 0 }
        val external = result.externalEvidence.sumOf {
            when (it.verdict) {
                ReputationVerdict.CONFIRMED -> 35
                ReputationVerdict.SUSPICIOUS -> 18
                ReputationVerdict.CONFLICTING -> 10
                else -> 0
            }
        }
        return (ml + local + structural + external).coerceIn(0, 100)
    }

    @Test
    fun `print the calibration matrix`() = runTest {
        val rows = mutableListOf<Triple<String, Int, String>>()

        suspend fun row(name: String, useCase: AnalyzeTextUseCase, text: String) {
            val result = useCase(text)
            rows += Triple(
                name,
                result.risk.score,
                "${result.risk.level} / confidence=${result.risk.confidence} " +
                    "/ before=${legacyScore(result)}",
            )
        }

        row("A benign message", textUseCase(), benign)
        row("D OTP scam", textUseCase(), otpScam)
        row("C bank impersonation", textUseCase(), bankImpersonation)
        row("E payment scam", textUseCase(), paymentScam)
        row("B suspicious URL (in message)", textUseCase(), "Please open $hostileUrl")

        row(
            "F + VirusTotal MALICIOUS",
            textUseCase(StubSource("VirusTotal", ReputationVerdict.CONFIRMED)),
            "Please open $hostileUrl",
        )
        row(
            "F + both providers MALICIOUS",
            textUseCase(
                StubSource("VirusTotal", ReputationVerdict.CONFIRMED),
                StubSource("URLhaus", ReputationVerdict.CONFIRMED),
            ),
            "Please open $hostileUrl",
        )
        row(
            "G + both NO_DETECTION",
            textUseCase(
                StubSource("VirusTotal", ReputationVerdict.NO_DETECTION),
                StubSource("URLhaus", ReputationVerdict.NO_DETECTION),
            ),
            "Please open $hostileUrl",
        )
        row(
            "H + both UNKNOWN",
            textUseCase(
                StubSource("VirusTotal", ReputationVerdict.UNKNOWN),
                StubSource("URLhaus", ReputationVerdict.UNKNOWN),
            ),
            "Please open $hostileUrl",
        )
        row(
            "C + both providers MALICIOUS",
            textUseCase(
                StubSource("VirusTotal", ReputationVerdict.CONFIRMED),
                StubSource("URLhaus", ReputationVerdict.CONFIRMED),
            ),
            bankImpersonation,
        )

        val bareUrl = AnalyzeUrlUseCase(urlAnalyzer, engine)(hostileUrl)
        rows += Triple(
            "B suspicious URL (Check URL)",
            bareUrl.risk.score,
            "${bareUrl.risk.level} / confidence=${bareUrl.risk.confidence} " +
                "/ before=${legacyScore(bareUrl)}",
        )

        println("CALIBRATION MATRIX")
        rows.forEach { (name, score, level) ->
            println("CAL | %-34s | %3d | %s".format(name, score, level))
        }

        println("REASONS for bank impersonation:")
        textUseCase()(bankImpersonation).risk.reasons.forEach { println("CALR |   $it") }
    }
}
