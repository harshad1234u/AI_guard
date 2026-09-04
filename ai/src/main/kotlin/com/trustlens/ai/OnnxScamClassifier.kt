package com.trustlens.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.trustlens.domain.AiClassifier
import com.trustlens.domain.ClassificationResult
import com.trustlens.domain.InferenceBackend
import com.trustlens.domain.MlSignal
import com.trustlens.domain.ScamCategory
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The on-device scam classifier: TF-IDF features in Kotlin, logistic regression in ONNX
 * Runtime on the CPU.
 *
 * ## What this model is
 *
 * Trained on the UCI SMS Spam Collection — 5,574 real, human-labelled SMS. It answers one
 * question: does this text look like SMS spam? Held-out performance is recorded in
 * `tools/model-metrics.json` and is accuracy *on that corpus*, which is not the same claim
 * as "detects scams".
 *
 * ## What it deliberately is not
 *
 * It was not trained on Indian banking scams — the corpus contains no UPI IDs, no KYC
 * fraud and no OTP harvesting. Those are the deterministic layer's job, and the risk engine
 * caps this model's contribution at 30 of 100 precisely so a single model trained on
 * someone else's data cannot decide a verdict on its own.
 *
 * ## Honesty rules this class follows
 *
 *  - [InferenceBackend.CPU] is reported because the CPU is what executes. There is no NPU or
 *    GPU path here, and none is claimed.
 *  - [ScamCategory.UNKNOWN] is always reported, because a binary spam/ham model genuinely
 *    cannot name a category. Inventing one from keywords would be the mock wearing a
 *    trained model's clothes.
 *  - Every failure — missing asset, corrupt model, inference error — yields
 *    [ClassificationResult.Unavailable]. The pipeline then continues on rules alone, and the
 *    UI says the model did not run. A classifier that cannot run must never look like a
 *    classifier that found nothing.
 */
class OnnxScamClassifier(private val context: Context) : AiClassifier {

    override val backend = InferenceBackend.CPU

    private val loadMutex = Mutex()
    private var loaded = false
    private var session: OrtSession? = null
    private var vectoriser: TfidfVectoriser? = null
    private var loadFailure: String? = null

    override suspend fun classify(text: String): ClassificationResult {
        if (text.isBlank()) return ClassificationResult.Unavailable("No content to analyse")

        val ready = ensureLoaded()
        if (!ready) {
            return ClassificationResult.Unavailable(
                loadFailure ?: "The on-device model could not be loaded",
            )
        }

        val activeSession = session ?: return ClassificationResult.Unavailable(
            "The on-device model is not available",
        )
        val activeVectoriser = vectoriser ?: return ClassificationResult.Unavailable(
            "The on-device model is not available",
        )

        return withContext(Dispatchers.Default) {
            runCatching { infer(activeSession, activeVectoriser, text) }
                .getOrElse {
                    ClassificationResult.Unavailable(
                        it.message ?: "On-device inference failed",
                    )
                }
        }
    }

    private fun infer(
        session: OrtSession,
        vectoriser: TfidfVectoriser,
        text: String,
    ): ClassificationResult {
        val features = vectoriser.transform(text)
        val environment = OrtEnvironment.getEnvironment()

        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(features),
            longArrayOf(1, features.size.toLong()),
        ).use { input ->
            session.run(mapOf(INPUT_NAME to input)).use { output ->
                @Suppress("UNCHECKED_CAST")
                val probabilities = output.get(PROBABILITIES_NAME)
                    .orElse(null)
                    ?.value as? Array<FloatArray>
                    ?: return ClassificationResult.Unavailable(
                        "The model returned an unexpected output",
                    )

                val scamProbability = probabilities[0][SPAM_CLASS].toDouble()
                    .coerceIn(0.0, 1.0)

                return ClassificationResult.Success(
                    MlSignal(
                        modelVersion = MODEL_VERSION,
                        scamProbability = scamProbability,
                        // A binary model cannot name a category, so it does not pretend to.
                        category = ScamCategory.UNKNOWN,
                        // Distance from the decision boundary, scaled to 0..1. This is a
                        // statement about how far from undecided the model is — not an
                        // accuracy claim, and not a probability of being correct.
                        confidence = (abs(scamProbability - 0.5) * 2.0).coerceIn(0.0, 1.0),
                        backend = InferenceBackend.CPU,
                    ),
                )
            }
        }
    }

    /** Loads the model and vocabulary once. A failure is remembered, not retried per call. */
    private suspend fun ensureLoaded(): Boolean {
        if (loaded) return session != null

        return loadMutex.withLock {
            if (loaded) return@withLock session != null

            withContext(Dispatchers.IO) {
                runCatching {
                    val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
                    val environment = OrtEnvironment.getEnvironment()
                    session = environment.createSession(
                        modelBytes,
                        OrtSession.SessionOptions(),
                    )
                    vectoriser = context.assets.open(VOCABULARY_ASSET).use { stream ->
                        stream.bufferedReader().useLines { TfidfVectoriser(TfidfVocabulary.parse(it)) }
                    }
                }.onFailure { error ->
                    // The class name matters as much as the message here: OrtException,
                    // UnsatisfiedLinkError and FileNotFoundException are three different
                    // problems, and several of them carry a null message.
                    android.util.Log.e(TAG, "Model load failed", error)
                    val detail = error.message?.takeIf { it.isNotBlank() }
                        ?: error::class.java.simpleName
                    loadFailure = "On-device model unavailable: $detail"
                    session = null
                    vectoriser = null
                }
            }

            loaded = true
            session != null
        }
    }

    private companion object {
        const val TAG = "TrustLensClassifier"

        /**
         * Identifies the corpus, the architecture and the revision, so a result can always
         * be traced back to what produced it.
         */
        const val MODEL_VERSION = "uci-sms-tfidf-logreg-v1"

        const val MODEL_ASSET = "scam-clf.onnx"
        const val VOCABULARY_ASSET = "scam-clf-vocab.tsv"

        const val INPUT_NAME = "input"
        const val PROBABILITIES_NAME = "probabilities"

        /** Column 1 of the output is the positive (spam) class. */
        const val SPAM_CLASS = 1
    }
}
