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
        _uiState.update { it.copy(message = value, result = null) }
    }

    fun analyse() {
        val message = _uiState.value.message
        if (message.isBlank() || _uiState.value.isAnalysing) return

        _uiState.update { it.copy(isAnalysing = true, result = null) }
        viewModelScope.launch {
            val result = analyzeText(message)
            _uiState.update { it.copy(isAnalysing = false, result = result) }
        }
    }

    fun reset() {
        _uiState.value = TextCheckUiState()
    }
}
