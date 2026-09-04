package com.trustlens.feature.qrscan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * A CameraX frame analyzer that decodes QR codes with bundled ML Kit.
 *
 * This lives in the feature module rather than `:ai` because it is inseparable from the frame
 * pipeline — it consumes an [ImageProxy] and obeys CameraX's backpressure contract. Everything
 * that can be reasoned about without a camera (payload classification and analysis) lives in
 * `:domain` and `:investigation`, where it is tested on the JVM.
 *
 * Two responsibilities, and only two: decode, and hand back a string. It draws no conclusion
 * about the payload and takes no action on it.
 */
internal class QrCodeAnalyzer(
    private val onPayload: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    /**
     * QR only. Narrowing the formats keeps the detector from spending every frame hunting for
     * barcode types TrustLens has no analysis for.
     */
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            // Nothing to decode — but the frame still has to be released, or the whole
            // analysis pipeline stalls after a handful of frames.
            imageProxy.close()
            return
        }

        val input = runCatching {
            InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
        }.getOrElse {
            imageProxy.close()
            return
        }

        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                // A frame can contain several codes. Taking the first non-empty payload
                // deterministically beats guessing which one the user meant, and the user can
                // always frame a single code to choose.
                barcodes.asSequence()
                    .mapNotNull { it.rawValue }
                    .firstOrNull { it.isNotBlank() }
                    ?.let(onPayload)
            }
            // Close on completion, success or failure alike. This is the single most
            // important line in the file: an unclosed ImageProxy freezes the preview.
            .addOnCompleteListener { imageProxy.close() }
    }

    fun release() {
        runCatching { scanner.close() }
    }
}
