package com.trustlens.feature.lensscan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustlens.domain.ScanOutcome
import com.trustlens.feature.result.RiskResultView

/**
 * Lens Scan.
 *
 * Point the camera at a suspicious message, capture one frame, and the recognised text goes
 * through the same pipeline as a pasted message. The camera does not detect anything by
 * itself — OCR extracts words, and the existing analysis judges them.
 */
@Composable
fun LensScanScreen(
    onBack: () -> Unit,
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

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("Back") }
            }

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
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Camera access needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Lens Scan reads a suspicious message through the camera. " +
                "The image stays on this device and is never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (wasDenied) {
            Text(
                text = "Permission was declined. You can still use Check Message to paste " +
                    "text instead, or grant camera access in Settings.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onRequest) { Text("Allow camera") }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Lens Scan", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Point at the message and tap Scan. One picture is taken and read " +
                "on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        camera.start(view)
                    }
                },
            )
            if (isWorking) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        Button(
            onClick = { camera.capture(onCaptured) },
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Text(if (isWorking) "Reading…" else "Scan")
        }
    }
}

@Composable
private fun ScanResult(state: LensScanUiState, onRescan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scan result", style = MaterialTheme.typography.headlineSmall)

        state.cameraError?.let { error ->
            Notice("Camera problem: $error")
            OutlinedButton(onClick = onRescan) { Text("Try again") }
        }

        when (val outcome = state.outcome) {
            is ScanOutcome.Analysed -> {
                Notice(
                    "Source: Camera / OCR. The text below was read from the picture and " +
                        "analysed exactly like a pasted message.",
                )
                if (outcome.isPartial) {
                    Notice(
                        "Only a small amount of text was read, so this assessment is based " +
                            "on partial content. Consider scanning again.",
                    )
                }

                RiskResultView(result = outcome.result)

                RecognisedTextCard(outcome.recognisedText)
            }

            ScanOutcome.NoTextFound -> NoTextGuidance()

            is ScanOutcome.Failed -> {
                Notice("Could not read the picture: ${outcome.reason}")
            }

            null -> Unit
        }

        Button(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text("Scan again")
        }
    }
}

@Composable
private fun NoTextGuidance() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "No readable text detected.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("No assessment was made, because nothing was read.")
        listOf(
            "Move closer to the message",
            "Improve the lighting",
            "Hold the phone steady",
            "Make sure the whole message is in frame",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * The extracted text, shown so the user can judge whether OCR read the message correctly.
 * Rendered as inert text — links inside it are not clickable.
 */
@Composable
private fun RecognisedTextCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Extracted text",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
