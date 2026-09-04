package com.trustlens.feature.qrscan

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX plumbing for the QR scanner.
 *
 * Unlike Lens Scan — which takes one picture when the user asks — a QR scanner has to look at
 * frames continuously, because the user expects a code to be recognised by pointing at it. So
 * this binds `Preview` + `ImageAnalysis`, with two limits that keep that honest:
 *
 *  - **[ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST]** — frames that arrive while a decode is in
 *    flight are dropped rather than queued. The scanner stays on the newest view of the world
 *    and can never build a backlog of stale frames.
 *  - **Nothing leaves the device and nothing is kept.** Each frame is decoded locally and
 *    closed. No frame is written to storage, uploaded, or retained after analysis.
 *
 * `ImageCapture` is deliberately not bound: a QR scanner has no reason to take a photograph.
 */
internal class QrCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onPayload: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: QrCodeAnalyzer? = null
    private var executor: ExecutorService? = null
    private var released = false

    fun start(previewView: PreviewView) {
        if (released) return

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (released) return@addListener

                val provider = runCatching { future.get() }.getOrElse {
                    onError(it.message ?: "Camera is unavailable on this device")
                    return@addListener
                }
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val analysisExecutor = Executors.newSingleThreadExecutor()
                executor = analysisExecutor

                val decoder = QrCodeAnalyzer(onPayload)
                analyzer = decoder

                val analysis = ImageAnalysis.Builder()
                    // Drop stale frames instead of queueing them: the scanner should always be
                    // looking at what the camera sees now.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply { setAnalyzer(analysisExecutor, decoder) }

                runCatching {
                    // Unbind first so a recomposition can never leave two analyzers running
                    // against the same camera.
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure {
                    onError(it.message ?: "Could not start the camera")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Releases the camera, the decoder and the analysis thread. Safe to call twice. */
    fun release() {
        released = true
        runCatching { cameraProvider?.unbindAll() }
        analyzer?.release()
        executor?.shutdown()
        cameraProvider = null
        analyzer = null
        executor = null
    }
}
