package com.trustlens

import com.google.common.truth.Truth.assertThat
import com.trustlens.ai.MockScamClassifier
import com.trustlens.domain.AnalyzeTextUseCase
import com.trustlens.domain.AnalyzeUrlUseCase
import com.trustlens.domain.Confidence
import com.trustlens.domain.ObservationKind
import com.trustlens.domain.RiskLevel
import com.trustlens.domain.SafeAction
import com.trustlens.investigation.DeterministicUrlAnalyzer
import com.trustlens.investigation.RuleBasedIndicatorExtractor
import com.trustlens.riskengine.RiskEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * End-to-end tests for URL investigation over the real components, wired as the app wires
 * them. Every one of these runs entirely offline â€” if any part of the analysis path reached
 * the network, these tests could not pass in a sandbox with no connectivity.
 */
class UrlFlowIntegrationTest {

    private fun urlUseCase() = AnalyzeUrlUseCase(
        urlAnalyzer = DeterministicUrlAnalyzer(),
        riskAssessor = RiskEngine(),
    )

    private fun textUseCase() = AnalyzeTextUseCase(
        extractor = RuleBasedIndicatorExtractor(),
        urlAnalyzer = DeterministicUrlAnalyzer(),
        classifier = MockScamClassifier(),
        riskAssessor = RiskEngine(),
    )

    // --- URL flow ----------------------------------------------------------------------

    @Test
    fun `a benign url scores low`() = runTest {
        val result = urlUseCase()("https://www.example.com/help")

        assertThat(result.risk.level).isEqualTo(RiskLevel.LOW)
    }

    @Test
    fun `a shortener produces a shortener observation and raises the score`() = runTest {
        val benign = urlUseCase()("https://www.example.com/help")
        val shortened = urlUseCase()("https://bit.ly/3xYzAbc")

        assertThat(shortened.observations.map { it.kind })
            .contains(ObservationKind.URL_SHORTENER)
        assertThat(shortened.risk.score).isGreaterThan(benign.risk.score)
    }

    @Test
    fun `an ip literal url is flagged`() = runTest {
        val result = urlUseCase()("http://203.0.113.10/verify")

        assertThat(result.observations.map { it.kind })
            .contains(ObservationKind.IP_ADDRESS_URL)
    }

    @Test
    fun `a punycode url is flagged`() = runTest {
        val result = urlUseCase()("https://xn--80ak6aa92e.com/login")

        assertThat(result.observations.map { it.kind })
            .contains(ObservationKind.PUNYCODE_DOMAIN)
    }

    @Test
    fun `a claimed organisation linking elsewhere is a mismatch`() = runTest {
        val result = urlUseCase()("https://sbi-kyc.example.com/verify", claimedOrganisation = "SBI")

        assertThat(result.observations.map { it.kind })
            .contains(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `the genuine organisation domain is not a mismatch`() = runTest {
        val result = urlUseCase()("https://onlinesbi.sbi/personal", claimedOrganisation = "SBI")

        assertThat(result.observations.map { it.kind })
            .doesNotContain(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `the url flow never claims external verification`() = runTest {
        val result = urlUseCase()("https://bit.ly/abc")

        assertThat(result.externalEvidence).isEmpty()
        assertThat(result.risk.degraded).isTrue()
        assertThat(result.risk.confidence).isNotEqualTo(Confidence.HIGH)
    }

    @Test
    fun `the url flow does not use the text classifier`() = runTest {
        val result = urlUseCase()("https://example.com/x")

        assertThat(result.context.mlSignal).isNull()
        assertThat(result.classifierBackend).isNull()
        assertThat(result.usedMockClassifier).isFalse()
    }

    @Test
    fun `an unparseable url yields a zero score rather than an error`() = runTest {
        val result = urlUseCase()("http://")

        assertThat(result.risk.score).isEqualTo(0)
        assertThat(result.observations).isEmpty()
    }

    @Test
    fun `url analysis is deterministic`() = runTest {
        val url = "https://sbi.secure.login.example.com:8443/update.apk"

        assertThat((1..25).map { urlUseCase()(url) }.distinct()).hasSize(1)
    }

    @Test
    fun `recommended actions for a risky link never include opening it`() = runTest {
        val result = urlUseCase()("https://sbi-kyc.example.com/verify", claimedOrganisation = "SBI")

        assertThat(result.risk.recommendedActions).contains(SafeAction.DO_NOT_OPEN_LINK)
        assertThat(SafeAction.entries.map { it.name }).containsNoneOf("OPEN_URL", "VISIT_LINK")
    }

    // --- URL analysis now feeds the text flow -------------------------------------------

    @Test
    fun `the text flow now picks up url structure from the message`() = runTest {
        val result = textUseCase()(
            "Your SBI account is blocked. Verify at https://sbi-kyc.example.com/verify",
        )

        val kinds = result.observations.map { it.kind }
        assertThat(kinds).contains(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `a shortened link inside a message is detected by the text flow`() = runTest {
        val result = textUseCase()("Claim your refund now at https://bit.ly/3xYzAbc")

        assertThat(result.observations.map { it.kind })
            .contains(ObservationKind.URL_SHORTENER)
    }

    @Test
    fun `adding url analysis raises the score of the hero message`() = runTest {
        val result = textUseCase()(
            "URGENT: Your SBI account will be blocked today. Complete KYC at " +
                "https://sbi-kyc.example.com/verify and share your OTP, or pay Rs. 4999 " +
                "to fraudster@ybl.",
        )

        assertThat(result.risk.level).isEqualTo(RiskLevel.CRITICAL)
        assertThat(result.observations.map { it.kind })
            .contains(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `a benign message with a legitimate link stays low risk`() = runTest {
        val result = textUseCase()("Here is the help page: https://www.example.com/help")

        assertThat(result.risk.level).isEqualTo(RiskLevel.LOW)
    }
}
