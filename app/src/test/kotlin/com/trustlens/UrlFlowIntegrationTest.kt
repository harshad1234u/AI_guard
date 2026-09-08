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

    // --- Local structural detection without threat intelligence -------------------------
    //
    // The point of these: a brand-new phishing domain has no reputation history, so if
    // TrustLens can only recognise it once a provider has heard of it, it cannot protect
    // anyone from a fresh campaign. Threat intelligence corroborates; it is not a
    // prerequisite. Bands and orderings only — the weights stay the engine's business.

    @Test
    fun `a brand impersonation on a free tld is guarded without any threat intelligence`() =
        runTest {
            val result = urlUseCase()("https://sbi.bank.tk/verify")

            assertThat(result.externalEvidence).isEmpty()
            assertThat(result.risk.level).isAtLeast(RiskLevel.GUARDED)
            assertThat(result.observations.map { it.kind }).containsAtLeast(
                ObservationKind.DOMAIN_ORGANISATION_MISMATCH,
                ObservationKind.SUSPICIOUS_URL,
            )
            assertThat(result.risk.recommendedActions).contains(SafeAction.DO_NOT_OPEN_LINK)
        }

    @Test
    fun `the impersonating link outranks the organisation's real domain`() = runTest {
        val impersonation = urlUseCase()("https://sbi.bank.tk/verify")
        val official = urlUseCase()("https://www.sbi.co.in/login")

        assertThat(impersonation.risk.score).isGreaterThan(official.risk.score)
        assertThat(official.risk.level).isEqualTo(RiskLevel.LOW)
    }

    @Test
    fun `legitimate domains gain no structural signal`() = runTest {
        listOf(
            "https://www.sbi.co.in/login",
            "https://onlinesbi.sbi",
            "https://www.icicibank.com",
            "https://example.com/about",
            "https://some-small-blog.in/post/1",
            "https://github.com",
        ).forEach { url ->
            val result = urlUseCase()(url)

            assertThat(result.observations).isEmpty()
            assertThat(result.risk.score).isEqualTo(0)
            assertThat(result.risk.level).isEqualTo(RiskLevel.LOW)
        }
    }

    @Test
    fun `a bare pasted link reaches the same analysis as a scheme qualified one`() = runTest {
        val bare = textUseCase()("Dear customer, verify at sbi.bank.tk/verify today")
        val qualified =
            textUseCase()("Dear customer, verify at https://sbi.bank.tk/verify today")

        listOf(bare, qualified).forEach { result ->
            assertThat(result.context.indicators.domains).contains("sbi.bank.tk")
            assertThat(result.observations.map { it.kind }).containsAtLeast(
                ObservationKind.DOMAIN_ORGANISATION_MISMATCH,
                ObservationKind.SUSPICIOUS_URL,
            )
        }
    }

    // --- A pasted link is not put to the SMS classifier ---------------------------------
    //
    // Over the real extractor and analyser, so these prove the guard agrees with what
    // TrustLens actually considers a link rather than with a second opinion about it.

    @Test
    fun `pasting a link into the message screen does not invoke the classifier`() = runTest {
        listOf(
            "https://www.sbi.co.in",
            "http://example.com",
            "sbi.bank.tk/verify",
            "example.com/login",
            "www.example.com",
        ).forEach { url ->
            val result = textUseCase()(url)

            assertThat(result.context.mlSignal).isNull()
            assertThat(result.classifierBackend).isNull()
            assertThat(result.classifierUnavailableReason).isNotNull()
        }
    }

    @Test
    fun `the official bank domain no longer collects a classifier contribution`() = runTest {
        // The regression this guard exists for: the genuine SBI domain used to score p=0.8888
        // on a corpus of UK SMS and contribute +26, out-scoring the phish impersonating it.
        val result = textUseCase()("https://www.sbi.co.in")

        assertThat(result.context.mlSignal).isNull()
        assertThat(result.risk.level).isEqualTo(RiskLevel.LOW)
        assertThat(result.observations).isEmpty()
    }

    @Test
    fun `a pasted phishing link keeps its deterministic signals without the classifier`() =
        runTest {
            val result = textUseCase()("sbi.bank.tk/verify")

            assertThat(result.context.mlSignal).isNull()
            assertThat(result.observations.map { it.kind }).containsExactly(
                ObservationKind.SUSPICIOUS_URL,
                ObservationKind.DOMAIN_ORGANISATION_MISMATCH,
            )
            assertThat(result.risk.level).isEqualTo(RiskLevel.GUARDED)
        }

    @Test
    fun `the impersonating link outscores the real one on the message screen too`() = runTest {
        val phish = textUseCase()("sbi.bank.tk/verify")
        val official = textUseCase()("https://www.sbi.co.in")

        // Before the guard this ordering was inverted: 5 against 26.
        assertThat(phish.risk.score).isGreaterThan(official.risk.score)
    }

    @Test
    fun `a message wrapped around a link still reaches the classifier`() = runTest {
        listOf(
            "URGENT verify:\nhttps://sbi.bank.tk/verify",
            "Your SBI account is blocked.\nhttps://sbi.bank.tk/verify\nSend OTP.",
        ).forEach { text ->
            val result = textUseCase()(text)

            assertThat(result.context.mlSignal).isNotNull()
            assertThat(result.observations.map { it.kind })
                .contains(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
        }
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
