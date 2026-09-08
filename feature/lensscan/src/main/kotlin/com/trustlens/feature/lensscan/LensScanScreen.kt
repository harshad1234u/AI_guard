package com.trustlens.feature.lensscan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustlens.core.design.Corner
import com.trustlens.core.design.EmptyState
import com.trustlens.core.design.ErrorState
import com.trustlens.core.design.LoadingState
import com.trustlens.core.design.NoticeCard
import com.trustlens.core.design.PrimaryButton
import com.trustlens.core.design.QuotedContent
import com.trustlens.core.design.ScreenHeader
import com.trustlens.core.design.SecondaryButton
import com.trustlens.core.design.Space
import com.trustlens.core.design.TrustLens
import com.trustlens.domain.ScanOutcome
import com.trustlens.feature.result.RiskResultView

/**
 * Lens Scan — the camera reading *text*, not codes.
 *
 * Point the camera at a suspicious message, capture one frame, and the recognised words go
 * through the same pipeline as a pasted message. The camera detects nothing by itself: OCR
 * extracts text and the existing analysis judges it.
 *
 * The framing guide is a wide, text-shaped rectangle rather than the square reticle a QR
 * scanner uses. That difference is the whole point of the screen — someone who mistakes this
 * for the QR scanner will hold the phone wrong and get nothing.
 */
@Composable
fun LensScanScreen(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: LensScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TrustLens.colors.screen,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Space.gutter),
        ) {
            ScreenHeader(
                title = "Scan with the camera",
                onBack = onBack,
                subtitle = "Point your camera at suspicious text.",
                modifier = Modifier.padding(top = Space.sm, bottom = Space.md),
            )

            when {
                !hasPermission -> CameraPermissionRationale(
                    wasDenied = permissionRequested,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )

                state.stage == ScanStage.RESULT -> ScanResult(
                    state = state,
                    onRescan = viewModel::rescan,
                )

                else -> CameraPane(
                    isWorking = state.stage == ScanStage.WORKING,
                    onCaptured = viewModel::onImageCaptured,
                    onError = viewModel::onCameraError,
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionRationale(wasDenied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = "Camera access needed",
            style = MaterialTheme.typography.titleLarge,
            color = TrustLens.colors.text,
        )
        Text(
            text = "Lens Scan reads a suspicious message through the camera. " +
                "The image stays on this device and is never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = TrustLens.colors.textMuted,
        )
        if (wasDenied) {
            NoticeCard(
                "Permission was declined. You can still use Check Message to paste text " +
                    "instead, or grant camera access in Settings.",
            )
        }
        PrimaryButton(text = "Allow camera", onClick = onRequest)
    }
}

@Composable
private fun CameraPane(
    isWorking: Boolean,
    onCaptured: (android.graphics.Bitmap, Int) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val camera = remember { CameraCapture(context, lifecycleOwner, onError) }

    // Release the camera when this screen leaves composition, so it is never held open.
    DisposableEffect(camera) {
        onDispose { camera.release() }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = "One picture is taken and read on this device. Nothing is stored, and " +
                "nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.textMuted,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(Corner.card),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        camera.start(view)
                    }
                },
            )

            // A wide, text-shaped guide. Deliberately not the square reticle of a QR scanner:
            // the two screens do different things and must not invite the same framing.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.46f)
                    .border(2.dp, TrustLens.colors.surface.copy(alpha = 0.85f), Corner.inner),
            )

            if (isWorking) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TrustLens.colors.screen.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingState(label = "Reading the text…")
                }
            }
        }

        PrimaryButton(
            text = if (isWorking) "Reading…" else "Scan",
            onClick = { camera.capture(onCaptured) },
            enabled = !isWorking,
            modifier = Modifier.padding(bottom = Space.lg),
        )
    }
}

@Composable
private fun ScanResult(state: LensScanUiState, onRescan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        state.cameraError?.let { error ->
            ErrorState(title = "Camera problem", body = error)
        }

        when (val outcome = state.outcome) {
            is ScanOutcome.Analysed -> {
                NoticeCard(
                    "Source: Camera / OCR. The text below was read from the picture and " +
                        "analysed exactly like a pasted message.",
                )
                if (outcome.isPartial) {
                    NoticeCard(
                        "Only a small amount of text was read, so this assessment is based " +
                            "on partial content. Consider scanning again.",
                    )
                }

                RiskResultView(result = outcome.result)

                QuotedContent(label = "Extracted text", text = outcome.recognisedText)
            }

            ScanOutcome.NoTextFound -> EmptyState(
                title = "No readable text detected",
                body = "No assessment was made, because nothing was read. That is not the " +
                    "same as finding nothing wrong.",
                hints = listOf(
                    "Move closer to the message",
                    "Improve the lighting",
                    "Hold the phone steady",
                    "Make sure the whole message is in frame",
                ),
            )

            is ScanOutcome.Failed -> ErrorState(
                title = "Could not read the picture",
                body = outcome.reason,
            )

            null -> Unit
        }

        SecondaryButton(
            text = "Scan again",
            onClick = onRescan,
            modifier = Modifier.padding(vertical = Space.lg),
        )
    }
}
