package com.trustlens.ai

import com.trustlens.domain.AiClassifier
import com.trustlens.domain.ClassificationResult
import com.trustlens.domain.InferenceBackend
import com.trustlens.domain.MlSignal
import com.trustlens.domain.ScamCategory
import javax.inject.Inject

/**
 * A stand-in classifier used to wire and demonstrate the pipeline. **This is not a machine
 * learning model.**
 *
 * It applies a small keyword table and reports the result with
 * [InferenceBackend.MOCK] and a model version that says so out loud, so no part of the app —
 * or of a demo — can mistake its output for a trained prediction. It exists so the UI and the
 * risk engine can be built and validated before the ONNX classifier is ready.
 *
 * Its probabilities carry **no accuracy guarantee of any kind**. They are not calibrated, not
 * evaluated against any dataset, and must never be quoted as precision, recall or confidence
 * in a real model.
 *
 * Replacing it is a one-line binding change in the DI module: the real ONNX CPU classifier
 * implements the same [AiClassifier] interface and nothing else in the app moves.
 */
class MockScamClassifier @Inject constructor() : AiClassifier {

    override val backend: InferenceBackend = InferenceBackend.MOCK

    override suspend fun classify(text: String): ClassificationResult {
        if (text.isBlank()) {
            return ClassificationResult.Unavailable("No content to analyse")
        }

        val lowered = text.lowercase()
        val hits = WEIGHTED_TERMS.filter { (term, _) -> term in lowered }

        // Deterministic and saturating: no randomness, no clock, no network.
        val rawScore = hits.values.sum()
        val probability = (rawScore / SATURATION_POINT).coerceIn(0.0, MAX_PROBABILITY)

        return ClassificationResult.Success(
            MlSignal(
                modelVersion = MODEL_VERSION,
                scamProbability = probability,
                category = categoryFor(lowered, probability),
                // Reported confidence stays deliberately modest: a keyword table has no
                // basis for claiming certainty.
                confidence = 0.4,
                backend = InferenceBackend.MOCK,
            ),
        )
    }

    private fun categoryFor(lowered: String, probability: Double): ScamCategory = when {
        probability < 0.2 -> ScamCategory.UNKNOWN
        "kyc" in lowered -> ScamCategory.KYC_FRAUD
        "otp" in lowered || "pin" in lowered -> ScamCategory.OTP_FRAUD
        "lottery" in lowered || "prize" in lowered || "won" in lowered ->
            ScamCategory.LOTTERY_PRIZE
        "job" in lowered || "salary" in lowered -> ScamCategory.JOB_FRAUD
        "delivery" in lowered || "parcel" in lowered || "courier" in lowered ->
            ScamCategory.DELIVERY_FRAUD
        "invest" in lowered || "trading" in lowered || "profit" in lowered ->
            ScamCategory.INVESTMENT_FRAUD
        "bank" in lowered || "account" in lowered -> ScamCategory.FINANCIAL_PHISHING
        else -> ScamCategory.UNKNOWN
    }

    private companion object {
        /** Deliberately unmistakable in logs, reports and screenshots. */
        const val MODEL_VERSION = "MOCK-0.1-NOT-A-TRAINED-MODEL"

        /** Weight total at which the mock reports its ceiling probability. */
        const val SATURATION_POINT = 10.0

        /** Never returns 1.0: a keyword table has no business reporting certainty. */
        const val MAX_PROBABILITY = 0.95

        val WEIGHTED_TERMS = mapOf(
            "otp" to 2.5,
            "one time password" to 2.5,
            "cvv" to 2.5,
            "pin" to 1.5,
            "password" to 1.5,
            "kyc" to 2.0,
            "blocked" to 1.5,
            "suspended" to 1.5,
            "deactivated" to 1.5,
            "urgent" to 1.5,
            "immediately" to 1.5,
            "verify" to 1.0,
            "click" to 1.0,
            "link" to 0.5,
            "lottery" to 2.0,
            "prize" to 1.5,
            "winner" to 1.5,
            "refund" to 1.0,
            "transfer" to 1.0,
            "upi" to 1.0,
            "aadhaar" to 1.5,
        )
    }
}
