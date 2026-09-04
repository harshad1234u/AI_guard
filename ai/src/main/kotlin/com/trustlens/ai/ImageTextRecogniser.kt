package com.trustlens.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trustlens.domain.OcrResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * On-device text recognition over a captured image.
 *
 * Deliberately takes a [Bitmap] rather than a CameraX type so the OCR adapter stays
 * independent of how the image was captured.
 */
interface ImageTextRecogniser {
    suspend fun recognise(bitmap: Bitmap, rotationDegrees: Int = 0): OcrResult
}

/**
 * ML Kit Text Recognition v2, bundled.
 *
 * The bundled model ships inside the APK, so recognition runs entirely on this device: the
 * captured image is never uploaded, and there is no first-run model download that could stall
 * a scan in front of a user.
 *
 * Recognised text is untrusted content. It is returned as a value and handed to the same
 * analysis pipeline a pasted message uses; nothing here interprets it.
 */
@Singleton
class MlKitTextRecogniser @Inject constructor() : ImageTextRecogniser {

    private val recogniser by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognise(bitmap: Bitmap, rotationDegrees: Int): OcrResult =
        suspendCoroutine { continuation ->
            runCatching { InputImage.fromBitmap(bitmap, rotationDegrees) }
                .onFailure {
                    continuation.resume(
                        OcrResult.Error(it.message ?: "Could not read the captured image"),
                    )
                    return@suspendCoroutine
                }
                .onSuccess { image ->
                    recogniser.process(image)
                        .addOnSuccessListener { text ->
                            val recognised = text.text
                            continuation.resume(
                                if (recognised.isBlank()) {
                                    OcrResult.NoTextFound
                                } else {
                                    OcrResult.Success(
                                        text = recognised,
                                        blockCount = text.textBlocks.size,
                                    )
                                },
                            )
                        }
                        .addOnFailureListener { error ->
                            // Recoverable: the user can retake the photo.
                            continuation.resume(
                                OcrResult.Error(error.message ?: "Text recognition failed"),
                            )
                        }
                }
        }
}
