package com.trustlens.domain

/** Where the analysed content came from. Every source converges on the same pipeline. */
enum class InvestigationSource {
    SMS,
    TEXT,
    URL,
    CAMERA_OCR,
    QR,
    VOICE,
    PHONE_NUMBER,
}

/** Which compute path actually executed an inference. Never inferred, always reported. */
enum class InferenceBackend {
    NPU,
    GPU,
    CPU,
    MOCK,
}

enum class ScamCategory {
    FINANCIAL_PHISHING,
    OTP_FRAUD,
    KYC_FRAUD,
    LOTTERY_PRIZE,
    JOB_FRAUD,
    DELIVERY_FRAUD,
    INVESTMENT_FRAUD,
    IMPERSONATION,
    UNKNOWN,
}

/**
 * Output of the on-device scam classifier.
 *
 * [backend] is reported by the adapter that ran the model and must reflect what actually
 * executed. Labelling a CPU run as NPU is a correctness bug, not a cosmetic one.
 */
data class MlSignal(
    val modelVersion: String,
    val scamProbability: Double,
    val category: ScamCategory,
    val confidence: Double,
    val backend: InferenceBackend,
) {
    init {
        require(scamProbability in 0.0..1.0) {
            "scamProbability must be within 0.0..1.0, was $scamProbability"
        }
        require(confidence in 0.0..1.0) {
            "confidence must be within 0.0..1.0, was $confidence"
        }
    }
}

/**
 * Deterministically extracted entities and request-type flags.
 *
 * These come from rule-based extraction, not from a language model — an LLM that hallucinates
 * a UPI ID would corrupt the risk score, so extraction never depends on one.
 */
data class Indicators(
    val urls: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val upiIds: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val organisations: List<String> = emptyList(),
    val amounts: List<String> = emptyList(),
    val requestedActions: List<String> = emptyList(),
    val urgency: Boolean = false,
    val otpRequest: Boolean = false,
    val paymentRequest: Boolean = false,
    val credentialRequest: Boolean = false,
    val accountThreat: Boolean = false,
    val impersonatedOrganisation: String? = null,
    /** Source spans for everything extracted, so evidence can be traced back to the input. */
    val matches: List<IndicatorMatch> = emptyList(),
)

/**
 * Everything known about one piece of content under investigation.
 *
 * [rawContent] is untrusted user-supplied data (Principle 1). It is carried as a value to be
 * analysed and displayed — never as an instruction, and never interpolated into a model's
 * instruction context.
 */
data class InvestigationContext(
    val source: InvestigationSource,
    val rawContent: String,
    val indicators: Indicators = Indicators(),
    val mlSignal: MlSignal? = null,
    val observations: List<Observation> = emptyList(),
    val externalEvidence: List<ExternalEvidence> = emptyList(),
    /** True when threat intelligence could not be consulted (offline or unconfigured). */
    val threatIntelUnavailable: Boolean = false,
)
