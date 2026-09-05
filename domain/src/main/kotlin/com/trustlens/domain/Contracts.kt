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

    /**
     * Looks up [subject], optionally telling the provider which concrete URLs on that host
     * the user is actually asking about.
     *
     * [observedUrls] never leaves the device. It exists so an adapter whose response already
     * enumerates offending URLs can decide, **locally**, whether the link in front of the
     * user is one of them — the difference between "this URL is reported malicious" and
     * "this hostname has reported malicious URLs". A large shared host is permanently listed
     * because someone once abused it, and without this distinction every link on it inherits
     * a verdict it did not earn.
     *
     * The transmitted payload is unchanged: still a hostname, never a path or query. The
     * default implementation ignores the context entirely, so a provider that cannot use it
     * needs no code.
     */
    suspend fun lookup(subject: String, observedUrls: List<String>): ExternalEvidence =
        lookup(subject)
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
