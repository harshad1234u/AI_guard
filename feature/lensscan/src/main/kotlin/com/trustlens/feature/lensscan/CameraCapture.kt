package com.trustlens.feature.lensscan

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Thin CameraX wrapper for the Lens Scan flow.
 *
 * Only [Preview] and [ImageCapture] are bound — deliberately no `ImageAnalysis`, because the
 * P0 design captures a single frame when the user asks. Nothing streams, nothing is analysed
 * in the background, and no frame is retained beyond the capture the user requested.
 */
internal class CameraCapture(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onError: (String) -> Unit,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    fun start(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val provider = runCatching { future.get() }.getOrElse {
                    onError(it.message ?: "Camera is unavailable on this device")
                    return@addListener
                }
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                runCatching {
                    // Rebinding from scratch avoids stacking use cases across recompositions.
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                }.onFailure {
                    onError(it.message ?: "Could not start the camera")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * Takes one picture and hands back a bitmap plus its rotation.
     *
     * The [ImageProxy] is closed in every path — leaving it open stalls the capture pipeline
     * and freezes the preview, which is a classic and very visible camera bug.
     */
    fun capture(onCaptured: (Bitmap, Int) -> Unit) {
        val capture = imageCapture ?: run {
            onError("Camera is not ready yet")
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        onCaptured(bitmap, rotation)
                    } catch (error: Throwable) {
                        onError(error.message ?: "Could not read the captured image")
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    this@CameraCapture.onError(
                        exception.message ?: "Could not capture an image",
                    )
                }
            },
        )
    }

    /** Unbinds every use case so the camera is released when the screen goes away. */
    fun release() {
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        imageCapture = null
    }
}
