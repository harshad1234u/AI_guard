package com.trustlens.feature.voicecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustlens.domain.AnalysisResult
import com.trustlens.domain.AnalyzeTranscriptUseCase
import com.trustlens.domain.RiskLevel
import com.trustlens.domain.TranscriptionBackend
import com.trustlens.domain.TranscriptionResult
import com.trustlens.domain.VoiceCheckOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Live Guard screen is currently showing. */
enum class LiveStage {
    /** Not listening. Nothing has been captured. */
    IDLE,

    /** Listening, and re-analysing as the conversation goes on. */
    RUNNING,

    /** Listening has ended. The last verdict stays on screen. */
    ENDED,
}

/** Whether the microphone is actually delivering audio, as far as the recogniser can tell. */
enum class ListeningHealth {
    OK,

    /**
     * Running, but nothing is arriving. On a live call this normally means the telephony
     * stack holds the microphone and the call is not on speaker.
     */
    NO_AUDIO,
}

data class LiveGuardUiState(
    val stage: LiveStage = LiveStage.IDLE,
    val health: ListeningHealth = ListeningHealth.OK,
    /** Everything recognised so far this session. This is what gets analysed. */
    val transcript: String = "",
    /** Interim words, shown only so the user can see the microphone working. */
    val partial: String = "",
    /** The most recent verdict over the whole transcript so far. */
    val result: AnalysisResult? = null,
    /**
     * The highest score reached at any point this session.
     *
     * A scammer says the dangerous sentence once. If the conversation then drifts onto
     * small talk, the score over the whole transcript can fall — the peak is what the user
     * needs to remember, so it is kept and shown alongside the live figure.
     */
    val peakScore: Int = 0,
    val peakLevel: RiskLevel? = null,
    val backend: TranscriptionBackend? = null,
    /**
     * The BCP 47 tag the recogniser last reported hearing, if it reported one.
     *
     * Display only. Detection tells the user which language TrustLens believes it is
     * following; it never reaches the risk pipeline, which reads Hindi, Hinglish and English
     * with the same rules and the same weights.
     */
    val detectedLanguage: String? = null,
    /** Set when listening stopped for a reason the user needs to see. */
    val problem: String? = null,
    val analysing: Boolean = false,
) {
    /** True once anything has crossed into the bands that warrant interrupting the user. */
    val alerting: Boolean
        get() = peakLevel != null && peakLevel >= RiskLevel.HIGH
}

/**
 * Drives one Live Guard session: continuous transcript -> the existing text analysis.
 *
 * No analysis logic lives here. Every verdict comes from [AnalyzeTranscriptUseCase] — the same
 * use case the single-shot Voice Check uses, which in turn delegates to the same
 * `AnalyzeTextUseCase` a pasted message goes through. Live Guard adds a *listening mode*, not
 * a detector: there is no "live" weight, no speech-specific signal, and nothing in this file
 * that can change a score.
 *
 * Two things it does own:
 *
 *  - **Restraint.** Re-analysing on every recognised phrase would run the classifier and the
 *    reputation providers dozens of times a minute. Analysis is debounced, and a transcript
 *    that has not changed is never analysed twice.
 *  - **Memory of the peak.** The live score describes the whole conversation so far; the peak
 *    records the worst moment in it. Both are shown, because a score that quietly falls after
 *    the dangerous sentence would be the wrong thing to leave on screen.
 */
@HiltViewModel
class LiveGuardViewModel @Inject constructor(
    private val analyzeTranscript: AnalyzeTranscriptUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveGuardUiState())
    val uiState: StateFlow<LiveGuardUiState> = _uiState.asStateFlow()

    private val segments = mutableListOf<String>()
    private var analysisJob: Job? = null
    private var lastAnalysedText: String? = null

    fun onListeningStarted(backend: TranscriptionBackend?) {
        segments.clear()
        lastAnalysedText = null
        analysisJob?.cancel()
        _uiState.value = LiveGuardUiState(stage = LiveStage.RUNNING, backend = backend)
    }

    /**
     * A completed phrase arrived.
     *
     * Only finished phrases are analysed. Interim results are shown but never scored: a
     * half-recognised sentence would produce a verdict about words nobody actually said.
     */
    fun onSegment(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        segments += trimmed
        val transcript = segments.joinToString(" ")
        _uiState.update { it.copy(transcript = transcript, partial = "") }

        scheduleAnalysis(transcript)
    }

    fun onPartial(text: String) {
        _uiState.update { it.copy(partial = text) }
    }

    /** The recogniser reported which language it is hearing. Caption only. */
    fun onLanguageDetected(tag: String) {
        _uiState.update { it.copy(detectedLanguage = tag) }
    }

    fun onHealth(health: ListeningHealth) {
        _uiState.update { it.copy(health = health) }
    }

    /** Listening ended — by the user, or because the recogniser could not continue. */
    fun onListeningEnded(problem: String? = null) {
        analysisJob?.cancel()
        val transcript = _uiState.value.transcript

        _uiState.update {
            it.copy(stage = LiveStage.ENDED, partial = "", problem = problem)
        }

        // One final pass, so words spoken just before the stop are never dropped.
        if (transcript.isNotBlank() && transcript != lastAnalysedText) {
            analysisJob = viewModelScope.launch { analyse(transcript) }
        }
    }

    fun reset() {
        segments.clear()
        lastAnalysedText = null
        analysisJob?.cancel()
        _uiState.value = LiveGuardUiState()
    }

    private fun scheduleAnalysis(transcript: String) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            // Settle first. A speaker mid-sentence produces several segments in a row, and
            // scoring each one would burn the classifier and the providers for a verdict
            // that is about to be replaced anyway.
            delay(ANALYSIS_DEBOUNCE_MILLIS)
            analyse(transcript)
        }
    }

    private suspend fun analyse(transcript: String) {
        if (transcript == lastAnalysedText) return
        lastAnalysedText = transcript

        _uiState.update { it.copy(analysing = true) }

        val outcome = runCatching {
            analyzeTranscript(
                TranscriptionResult.Success(
                    text = transcript,
                    backend = _uiState.value.backend ?: TranscriptionBackend.ON_DEVICE,
                ),
            )
        }.getOrElse { VoiceCheckOutcome.Failed(it.message ?: "Analysis failed") }

        _uiState.update { state ->
            when (outcome) {
                is VoiceCheckOutcome.Analysed -> {
                    val risk = outcome.result.risk
                    val isNewPeak = risk.score > state.peakScore
                    state.copy(
                        analysing = false,
                        result = outcome.result,
                        peakScore = if (isNewPeak) risk.score else state.peakScore,
                        peakLevel = if (isNewPeak) risk.level else state.peakLevel,
                    )
                }

                // Nothing usable was heard. That is not a clean verdict, so the previous
                // result and the peak are both left exactly as they were.
                else -> state.copy(analysing = false)
            }
        }
    }

    private companion object {
        /**
         * Long enough to let a sentence finish, short enough that a warning still lands
         * while the caller is still talking.
         */
        const val ANALYSIS_DEBOUNCE_MILLIS = 1_200L
    }
}
