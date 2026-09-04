package com.trustlens.domain

/**
 * Everything the UI needs to render one completed analysis.
 *
 * The three kinds of statement stay in separate fields all the way to the screen, so the
 * presentation layer cannot accidentally blend "what we saw", "what a source reported" and
 * "what we concluded" into one undifferentiated list.
 */
data class AnalysisResult(
    val context: InvestigationContext,
    val risk: RiskResult,
    /** Which backend actually produced [InvestigationContext.mlSignal], if any. */
    val classifierBackend: InferenceBackend?,
    /** Set when the classifier could not run; the assessment then rests on rules alone. */
    val classifierUnavailableReason: String? = null,
) {
    /**
     * True when the classification came from a stand-in rather than a trained model.
     *
     * The UI must surface this. A mock that silently looks like a real prediction is a
     * correctness problem, not a cosmetic one.
     */
    val usedMockClassifier: Boolean
        get() = classifierBackend == InferenceBackend.MOCK

    val observations: List<Observation> get() = risk.observations
    val externalEvidence: List<ExternalEvidence> get() = risk.externalEvidence
    val inferences: List<Inference> get() = risk.inferences
}
