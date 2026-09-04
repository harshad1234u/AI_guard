package com.trustlens.feature.lensscan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustlens.ai.ImageTextRecogniser
import com.trustlens.domain.AnalyzeScannedTextUseCase
import com.trustlens.domain.OcrResult
import com.trustlens.domain.ScanOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Lens Scan screen is currently showing. */
enum class ScanStage {
    /** Live preview, waiting for the user to press Scan. */
    PREVIEW,

    /** A capture is being recognised and analysed. */
    WORKING,

    /** A result, an empty read, or an error is on screen. */
    RESULT,
}

data class LensScanUiState(
    val stage: ScanStage = ScanStage.PREVIEW,
    val outcome: ScanOutcome? = null,
    /** Set when the camera itself failed to start or capture. */
    val cameraError: String? = null,
)

/**
 * Drives one scan: capture -> OCR -> the existing text analysis pipeline.
 *
 * No analysis logic lives here. Recognition is delegated to [ImageTextRecogniser] and every
 * judgement to [AnalyzeScannedTextUseCase], which is the same pipeline a pasted message uses.
 */
@HiltViewModel
class LensScanViewModel @Inject constructor(
    private val recogniser: ImageTextRecogniser,
    private val analyzeScannedText: AnalyzeScannedTextUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LensScanUiState())
    val uiState: StateFlow<LensScanUiState> = _uiState.asStateFlow()

    /**
     * Called once per user-initiated capture. The bitmap is recognised, analysed and then
     * dropped — nothing is written to storage or sent anywhere.
     */
    fun onImageCaptured(bitmap: Bitmap, rotationDegrees: Int) {
        _uiState.update { it.copy(stage = ScanStage.WORKING, cameraError = null) }

        viewModelScope.launch {
            val ocr = runCatching { recogniser.recognise(bitmap, rotationDegrees) }
                .getOrElse { OcrResult.Error(it.message ?: "Text recognition failed") }

            val outcome = analyzeScannedText(ocr)

            // The captured frame has served its purpose; release it rather than hold it.
            bitmap.recycle()

            _uiState.update { it.copy(stage = ScanStage.RESULT, outcome = outcome) }
        }
    }

    fun onCameraError(message: String) {
        _uiState.update {
            it.copy(stage = ScanStage.RESULT, outcome = null, cameraError = message)
        }
    }

    /** Return to the live preview to try again. */
    fun rescan() {
        _uiState.value = LensScanUiState()
    }
}
