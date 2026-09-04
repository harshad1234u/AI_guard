package com.trustlens.feature.urlcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustlens.domain.AnalysisResult
import com.trustlens.domain.AnalyzeUrlUseCase
import com.trustlens.domain.UrlAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the Check URL screen.
 *
 * [url] is untrusted input. It is analysed as a string and displayed as inert text — the
 * screen offers no way to open it.
 */
data class UrlCheckUiState(
    val url: String = "",
    val isAnalysing: Boolean = false,
    val result: AnalysisResult? = null,
    val analysis: UrlAnalysis? = null,
) {
    val canAnalyse: Boolean get() = url.isNotBlank() && !isAnalysing

    /** Set when the text could not be read as a URL at all. */
    val isUnparseable: Boolean get() = analysis?.isParseable == false
}

@HiltViewModel
class UrlCheckViewModel @Inject constructor(
    private val analyzeUrl: AnalyzeUrlUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UrlCheckUiState())
    val uiState: StateFlow<UrlCheckUiState> = _uiState.asStateFlow()

    fun onUrlChange(value: String) {
        _uiState.update { it.copy(url = value, result = null, analysis = null) }
    }

    /**
     * Structural analysis is still entirely offline. What is awaited now is a *reputation
     * lookup about the domain* — TrustLens asks VirusTotal and URLhaus what they know, and
     * still never contacts the link itself. That distinction is the whole safety property:
     * investigating a hostile URL must never mean visiting it.
     *
     * Every provider call is individually time-boxed, so this cannot hang the screen; if
     * they are slow or unreachable the result arrives marked as externally unverified.
     */
    fun analyse() {
        val url = _uiState.value.url
        if (url.isBlank() || _uiState.value.isAnalysing) return

        val analysis = analyzeUrl.inspect(url)
        _uiState.update { it.copy(isAnalysing = true, analysis = analysis, result = null) }

        viewModelScope.launch {
            val result = if (analysis.isParseable) analyzeUrl(url) else null
            _uiState.update { it.copy(isAnalysing = false, result = result) }
        }
    }
}
