package com.trustlens.ai

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * What the classifier can and cannot see.
 *
 * `OnnxScamClassifier` reports [com.trustlens.domain.ClassificationResult.Unavailable] when
 * the feature vector comes back entirely zero, because a logistic regression given a zero
 * vector returns its intercept — the model's prior, identical for every such input, and not a
 * measurement of anything. Measured on the shipped model that intercept is p(scam) = 0.101,
 * which the risk engine would turn into a flat `+3` on the screen under the words "On-device
 * classifier rates this as likely spam or scam wording".
 *
 * That guard is only correct if the premise is true, so the premise is tested here rather
 * than asserted in a comment. The classifier itself needs an Android `Context` and the asset
 * manager, so the vectoriser is where this can be pinned on the JVM.
 */
class TfidfVocabularyCoverageTest {

    private val vectoriser by lazy {
        TfidfVectoriser(
            File("src/main/assets/scam-clf-vocab.tsv").useLines { TfidfVocabulary.parse(it) },
        )
    }

    @Test
    fun `devanagari text has no vocabulary coverage at all`() {
        // The model was trained on the UCI SMS corpus. It contains no Devanagari, so a Hindi
        // scam and Hindi small talk are indistinguishable to it — both produce this.
        listOf(
            // "OTP" is left in on purpose: the UCI corpus predates OTP fraud and does not
            // contain that token at all, so even the English word carries no weight here.
            "मैं बैंक से बोल रहा हूँ। आपका अकाउंट बंद हो जाएगा। OTP बताइए।",
            "आज मौसम बहुत अच्छा है, हम शाम को बाहर घूमने चलेंगे।",
            "अभी आपके फोन पर छह अंकों का नंबर आया होगा, मुझे बताइए।",
        ).forEach { text ->
            assertThat(vectoriser.transform(text).all { it == 0f }).isTrue()
        }
    }

    @Test
    fun `english text does have coverage, so the model is genuinely consulted`() {
        val vector = vectoriser.transform(
            "Your account will be blocked. Please share your OTP immediately.",
        )

        assertThat(vector.any { it != 0f }).isTrue()
    }

    @Test
    fun `an empty input is indistinguishable from unseen vocabulary`() {
        // Which is exactly why both are reported as unavailable rather than scored.
        assertThat(vectoriser.transform("").all { it == 0f }).isTrue()
    }
}
