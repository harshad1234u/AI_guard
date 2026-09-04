package com.trustlens.domain

/**
 * On-device scam classifier. Implementations live in :ai — a mock, an ONNX CPU adapter, and
 * later an NPU adapter — and are swapped without any caller changing.
 */
interface AiClassifier {
    /** Reports which backend this implementation will actually use for [classify]. */
    val backend: InferenceBackend

    suspend fun classify(text: String): ClassificationResult
}

/**
 * Success carries a signal; failure is an ordinary value, not an exception, so that offline
 * and model-unavailable paths are handled at every call site by construction.
 */
sealed interface ClassificationResult {
    data class Success(val signal: MlSignal) : ClassificationResult

    /** The model could not run. The pipeline continues on rules alone. */
    data class Unavailable(val reason: String) : ClassificationResult
}

/**
 * An external reputation provider (VirusTotal, URLhaus).
 *
 * Implementations must map "no report", rate limiting, timeouts and offline conditions to
 * [ReputationVerdict.NO_DETECTION], [ReputationVerdict.UNKNOWN] or
 * [ReputationVerdict.ERROR] — never to an absence of evidence and never to safety.
 */
interface ReputationSource {
    val sourceName: String

    suspend fun lookup(subject: String): ExternalEvidence
}

/**
 * Calculates the final risk verdict.
 *
 * This interface exists so application code can depend on scoring without depending on the
 * engine module, which keeps :riskengine pointing only at :domain. Implementations must be
 * deterministic and free of IO — the score is code, never a model (Architecture Rule 3).
 */
interface RiskAssessor {
    fun assess(context: InvestigationContext): RiskResult
}

/**
 * Deterministic extraction of entities and request flags from text.
 *
 * Rule-based by contract: no model may be substituted here, because the risk engine trusts
 * these values as facts about the content.
 */
interface IndicatorExtractor {
    fun extract(text: String): Indicators
}
