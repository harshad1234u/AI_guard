package com.trustlens.feature.qrscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustlens.domain.AnalyzeQrPayloadUseCase
import com.trustlens.domain.QrScanOutcome
import com.trustlens.domain.ScanDebouncer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the QR screen is currently showing. */
enum class QrStage {
    /** Live preview, watching for a code. */
    SCANNING,

    /** A payload was decoded and is being analysed. */
    WORKING,

    /** A result, an empty decode, or a camera error is on screen. */
    RESULT,
}

data class QrScanUiState(
    val stage: QrStage = QrStage.SCANNING,
    val outcome: QrScanOutcome? = null,
    val cameraError: String? = null,
)

/**
 * Drives QR scanning: decode -> classify -> the existing analysis pipeline.
 *
 * No detection logic lives here. Every judgement comes from [AnalyzeQrPayloadUseCase], which
 * routes a payload to the same analysis that a typed URL or a pasted message uses.
 *
 * The one piece of behaviour this class does own is *restraint*: a continuous scanner sees the
 * same code in every frame, so a payload is analysed once and then the screen stops scanning
 * until the user asks for another. Decoding a code is never the same thing as acting on it.
 */
@HiltViewModel
class QrScanViewModel @Inject constructor(
    private val analyzeQrPayload: AnalyzeQrPayloadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrScanUiState())
    val uiState: StateFlow<QrScanUiState> = _uiState.asStateFlow()

    private val debouncer = ScanDebouncer()

    /**
     * Called from the camera's analysis thread for every frame that decodes to something.
     *
     * Guarded twice over: the stage check stops a second analysis while one is already running
     * or a result is on screen, and the debouncer stops the same code re-triggering when the
     * user returns to scanning with it still in frame.
     */
    fun onPayloadDetected(rawPayload: String, nowMillis: Long = System.currentTimeMillis()) {
        if (_uiState.value.stage != QrStage.SCANNING) return
        if (!debouncer.shouldProcess(rawPayload, nowMillis)) return

        _uiState.update { it.copy(stage = QrStage.WORKING, cameraError = null) }

        viewModelScope.launch {
            val analysis = runCatching { analyzeQrPayload(rawPayload) }

            _uiState.update { state ->
                analysis.fold(
                    onSuccess = { state.copy(stage = QrStage.RESULT, outcome = it) },
                    // A failed analysis must never be presented as a clean scan.
                    onFailure = {
                        state.copy(
                            stage = QrStage.RESULT,
                            outcome = null,
                            cameraError = "Could not analyse the code: " +
                                (it.message ?: "unexpected error"),
                        )
                    },
                )
            }
        }
    }

    fun onCameraError(message: String) {
        _uiState.update {
            it.copy(stage = QrStage.RESULT, outcome = null, cameraError = message)
        }
    }

    /** Return to the live preview, and allow the same code to be scanned again. */
    fun rescan() {
        debouncer.reset()
        _uiState.value = QrScanUiState()
    }
}
