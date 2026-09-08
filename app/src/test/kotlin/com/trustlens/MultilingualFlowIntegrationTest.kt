package com.trustlens

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.AiClassifier
import com.trustlens.domain.AnalyzeTextUseCase
import com.trustlens.domain.ClassificationResult
import com.trustlens.domain.InferenceBackend
import com.trustlens.domain.InvestigationSource
import com.trustlens.domain.ObservationKind
import com.trustlens.domain.RiskLevel
import com.trustlens.domain.SafeAction
import com.trustlens.investigation.DeterministicUrlAnalyzer
import com.trustlens.investigation.MultilingualIndicatorExtractor
import com.trustlens.investigation.RuleBasedIndicatorExtractor
import com.trustlens.riskengine.RiskEngine
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * End-to-end scoring for Hindi and Hinglish, over the production wiring.
 *
 * This is the test that states the actual claim, in points rather than in booleans: **a scam
 * is scored by what it asks for, not by the language it asks in.** The same demand in Hindi,
 * Hinglish and English must reach the same number, because it runs through the same
 * observations and the same `RiskWeights` — there is no language multiplier anywhere.
 *
 * The classifier is deliberately absent. That is not a convenience: for Devanagari the real
 * `OnnxScamClassifier` reports itself unavailable, because its UCI-SMS vocabulary contains no
 * Hindi and a zero feature vector would only return the model's prior. So "no ML signal" *is*
 * the production condition for Hindi, and these numbers are the ones a Hindi call really
 * reaches.
 */
class MultilingualFlowIntegrationTest {

    /** Reports what the real classifier reports for text it has no vocabulary for. */
    private class NoVocabularyCoverage : AiClassifier {
        override val backend = InferenceBackend.CPU
        override suspend fun classify(text: String) =
            ClassificationResult.Unavailable("no vocabulary coverage")
    }

    private fun pipeline(multilingual: Boolean) = AnalyzeTextUseCase(
        extractor = if (multilingual) {
            MultilingualIndicatorExtractor(RuleBasedIndicatorExtractor())
        } else {
            RuleBasedIndicatorExtractor()
        },
        urlAnalyzer = DeterministicUrlAnalyzer(),
        classifier = NoVocabularyCoverage(),
        riskAssessor = RiskEngine(),
    )

    private val analyse = pipeline(multilingual = true)
    private val frozenOnly = pipeline(multilingual = false)

    private val hindiScam =
        "मैं एसबीआई से बोल रहा हूँ। आपका अकाउंट आज बंद हो जाएगा। " +
            "अभी आपको एक OTP आया होगा, वो मुझे बताइए।"

    private val englishScam =
        "I am calling from SBI. Your account will be blocked today. " +
            "Please tell me the OTP immediately."

    private val hinglishScam =
        "Main SBI se bol raha hoon, aapka account block ho jayega, abhi OTP bataiye."

    // ---- the claim ------------------------------------------------------------------------

    @Test
    fun `the same demand scores identically in hindi and in english`() = runTest {
        val hindi = analyse(hindiScam, InvestigationSource.VOICE)
        val english = analyse(englishScam, InvestigationSource.VOICE)

        assertThat(hindi.risk.score).isEqualTo(english.risk.score)
        assertThat(hindi.risk.level).isEqualTo(english.risk.level)
        assertThat(hindi.risk.observations.map { it.kind })
            .containsExactlyElementsIn(english.risk.observations.map { it.kind })
    }

    @Test
    fun `a hindi scam call reaches the same observations an english one does`() = runTest {
        val kinds = analyse(hindiScam, InvestigationSource.VOICE).risk.observations.map { it.kind }

        assertThat(kinds).containsAtLeast(
            ObservationKind.OTP_REQUEST,
            ObservationKind.URGENCY,
            ObservationKind.ACCOUNT_THREAT,
            ObservationKind.ORGANISATION_IMPERSONATION,
        )
    }

    @Test
    fun `hinglish is scored, not treated as broken english`() = runTest {
        val result = analyse(hinglishScam, InvestigationSource.VOICE)

        assertThat(result.risk.observations.map { it.kind })
            .containsAtLeast(ObservationKind.OTP_REQUEST, ObservationKind.ACCOUNT_THREAT)
        assertThat(result.risk.recommendedActions).contains(SafeAction.DO_NOT_SHARE_OTP)
    }

    @Test
    fun `the user is told what to do, not merely given a number`() = runTest {
        val actions = analyse(hindiScam, InvestigationSource.VOICE).risk.recommendedActions

        assertThat(actions).contains(SafeAction.DO_NOT_SHARE_OTP)
        assertThat(actions).contains(SafeAction.VERIFY_VIA_OFFICIAL_CHANNEL)
        // Principle 3: nothing offered here opens, pays or installs anything.
        assertThat(actions.map { it.name })
            .containsNoneOf("PAY", "OPEN_URL", "LAUNCH_PAYMENT", "INSTALL_APP")
    }

    // ---- what the change is worth, stated as a measurement ---------------------------------

    @Test
    fun `the frozen pipeline alone scores this hindi scam as harmless`() = runTest {
        val before = frozenOnly(hindiScam, InvestigationSource.VOICE)
        val after = analyse(hindiScam, InvestigationSource.VOICE)

        // The gap this work exists to close. Recorded as a test so it cannot quietly reopen.
        assertThat(before.risk.level).isEqualTo(RiskLevel.LOW)
        assertThat(before.risk.observations).isEmpty()
        assertThat(after.risk.score).isGreaterThan(before.risk.score)
        assertThat(after.risk.level).isAtLeast(RiskLevel.MEDIUM)
    }

    @Test
    fun `english scoring is unchanged by the multilingual layer`() = runTest {
        listOf(
            englishScam,
            "URGENT: Your SBI account will be blocked today. Complete KYC at " +
                "https://sbi-kyc.example.com/verify and share your OTP, or pay Rs. 4999.",
            "123456 is your OTP for HDFC Bank. Do not share it with anyone.",
            "Hi, are we still meeting for lunch at one o'clock today?",
        ).forEach { text ->
            assertThat(analyse(text).risk.score).isEqualTo(frozenOnly(text).risk.score)
        }
    }

    // ---- privacy --------------------------------------------------------------------------

    @Test
    fun `no OTP digits reach the evidence shown to the user`() = runTest {
        val result = analyse(
            "अभी आपके फोन पर छह अंकों का नंबर 483920 आया होगा, वो बताइए।",
            InvestigationSource.VOICE,
        )

        assertThat(result.risk.observations.map { it.kind }).contains(ObservationKind.OTP_REQUEST)
        result.context.indicators.matches.forEach {
            assertThat(it.text).doesNotContain("483920")
        }
        result.risk.reasons.forEach { assertThat(it).doesNotContain("483920") }
    }

    @Test
    fun `ordinary hindi conversation is not flagged`() = runTest {
        val result = analyse(
            "आज मौसम बहुत अच्छा है, हम शाम को बाहर घूमने चलेंगे। कल मिलते हैं।",
            InvestigationSource.VOICE,
        )

        assertThat(result.risk.level).isEqualTo(RiskLevel.LOW)
        assertThat(result.risk.observations).isEmpty()
    }
}
