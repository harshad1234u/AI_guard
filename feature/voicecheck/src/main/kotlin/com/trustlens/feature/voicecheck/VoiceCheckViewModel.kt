package com.trustlens.feature.voicecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustlens.domain.AnalyzeTranscriptUseCase
import com.trustlens.domain.TranscriptionBackend
import com.trustlens.domain.TranscriptionResult
import com.trustlens.domain.VoiceCheckOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Voice Check screen is currently showing. */
enum class VoiceStage {
    /** Waiting for the user to start. The microphone is off. */
    IDLE,

    /** The microphone is on, at the user's request. */
    LISTENING,

    /** A transcript is being analysed. The microphone is already off. */
    WORKING,

    /** A verdict, an empty listen, or an error is on screen. */
    RESULT,
}

data class VoiceCheckUiState(
    val stage: VoiceStage = VoiceStage.IDLE,
    /** Interim words, shown only so the user can see the microphone is working. */
    val partialTranscript: String = "",
    val outcome: VoiceCheckOutcome? = null,
)

/**
 * Drives one voice check: listen -> transcript -> the existing text analysis pipeline.
 *
 * No analysis logic lives here, and nothing voice-specific reaches the score. Recognition is
 * delegated to [SpeechTranscriber] and every judgement to [AnalyzeTranscriptUseCase], which
 * hands the words to the same pipeline a pasted message uses.
 *
 * The interim transcript is display only. It is never analysed — scoring half a sentence and
 * then rescoring the whole one would flash a verdict the user has no reason to trust.
 */
@HiltViewModel
class VoiceCheckViewModel @Inject constructor(
    private val analyzeTranscript: AnalyzeTranscriptUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceCheckUiState())
    val uiState: StateFlow<VoiceCheckUiState> = _uiState.asStateFlow()

    /** Called when the user starts a listening session. */
    fun onListeningStarted() {
        _uiState.value = VoiceCheckUiState(stage = VoiceStage.LISTENING)
    }

    fun onPartialTranscript(text: String) {
        _uiState.update {
            if (it.stage == VoiceStage.LISTENING) it.copy(partialTranscript = text) else it
        }
    }

    /**
     * Called once a session ends, however it ended. The microphone is already released by the
     * time this runs — analysis never happens while the device is still listening.
     */
    fun onTranscription(transcription: TranscriptionResult) {
        _uiState.update { it.copy(stage = VoiceStage.WORKING) }

        viewModelScope.launch {
            val outcome = runCatching { analyzeTranscript(transcription) }.getOrElse {
                VoiceCheckOutcome.Failed(it.message ?: "Could not analyse the transcript")
            }
            _uiState.update { it.copy(stage = VoiceStage.RESULT, outcome = outcome) }
        }
    }

    /** The fallback route: the user typed the words instead of speaking them. */
    fun onManualText(text: String) {
        onTranscription(
            TranscriptionResult.Success(text, TranscriptionBackend.MANUAL_ENTRY),
        )
    }

    /** Back to the start. The microphone is not touched by this. */
    fun reset() {
        _uiState.value = VoiceCheckUiState()
    }
}
