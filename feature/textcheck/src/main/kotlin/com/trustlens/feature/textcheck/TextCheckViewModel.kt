package com.trustlens.feature.textcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustlens.domain.AnalysisResult
import com.trustlens.domain.AnalyzeTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Check Message screen.
 *
 * [message] is untrusted content the user pasted in. It is held to be analysed and displayed,
 * never acted upon.
 */
data class TextCheckUiState(
    val message: String = "",
    val isAnalysing: Boolean = false,
    val result: AnalysisResult? = null,
    /**
     * True when this text arrived from another app's selection toolbar rather than being
     * pasted here.
     *
     * Display only — it changes nothing about the analysis. The screen uses it to say that
     * only the highlighted fragment was read, so nobody can infer that TrustLens has access
     * to the app the text came from.
     */
    val fromSelection: Boolean = false,
    /**
     * Set when the selection was longer than the cap, carrying the original length so the
     * screen can state how much was analysed. Null means nothing was dropped.
     */
    val selectionOriginalLength: Int? = null,
) {
    val canAnalyse: Boolean get() = message.isNotBlank() && !isAnalysing
}

/**
 * Holds screen state and delegates all analysis to [AnalyzeTextUseCase].
 *
 * Deliberately thin: no scoring, no extraction and no classification happens here, so the
 * pipeline stays testable without a UI and the same verdict is produced regardless of which
 * screen invoked it.
 */
@HiltViewModel
class TextCheckViewModel @Inject constructor(
    private val analyzeText: AnalyzeTextUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextCheckUiState())
    val uiState: StateFlow<TextCheckUiState> = _uiState.asStateFlow()

    fun onMessageChange(value: String) {
        // Editing by hand ends the "this came from a selection" framing: what is in the box
        // is no longer what the other app handed over.
        _uiState.update {
            it.copy(
                message = value,
                result = null,
                fromSelection = false,
                selectionOriginalLength = null,
            )
        }
    }

    /**
     * Text highlighted in another app, delivered by `ACTION_PROCESS_TEXT`.
     *
     * Analysis starts immediately: the user already chose this text and chose TrustLens, so
     * making them press a second button would add a tap and no information.
     *
     * Blank input is *not* analysed. A verdict about nothing would read as "nothing wrong
     * here", which is the one thing an empty selection must never be allowed to say.
     *
     * @param originalLength the selection's length before the caller's cap, so truncation can
     *   be stated rather than hidden.
     */
    fun onSelectedText(text: String, originalLength: Int = text.length) =
        prefill(text, originalLength, fromSelection = true)

    /**
     * Plain text shared by another app with `ACTION_SEND`, carrying no email context.
     *
     * Identical to [onSelectedText] except that it does not claim the text was highlighted.
     * A message forwarded from Messages or WhatsApp was not a selection, and saying so would
     * be a false statement about where the characters came from.
     */
    fun onSharedText(text: String) = prefill(text, text.length, fromSelection = false)

    private fun prefill(text: String, originalLength: Int, fromSelection: Boolean) {
        if (text.isBlank()) {
            _uiState.value = TextCheckUiState(fromSelection = fromSelection)
            return
        }

        _uiState.value = TextCheckUiState(
            message = text,
            fromSelection = fromSelection,
            selectionOriginalLength = originalLength.takeIf { it > text.length },
        )
        analyse()
    }

    fun analyse() {
        val message = _uiState.value.message
        if (message.isBlank() || _uiState.value.isAnalysing) return

        _uiState.update { it.copy(isAnalysing = true, result = null) }
        viewModelScope.launch {
            val result = runCatching { analyzeText(message) }.getOrNull()
            _uiState.update { it.copy(isAnalysing = false, result = result) }
        }
    }

    fun reset() {
        _uiState.value = TextCheckUiState()
    }
}
