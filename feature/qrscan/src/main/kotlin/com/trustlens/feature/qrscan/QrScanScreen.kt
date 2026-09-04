package com.trustlens.feature.qrscan

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.trustlens.domain.QrPayload
import com.trustlens.domain.QrScanOutcome
import com.trustlens.domain.UpiPaymentRequest
import com.trustlens.feature.result.RiskResultView

/**
 * Scan QR.
 *
 * Points the camera at a code, decodes it on-device, and investigates the payload. What the
 * screen never does is act on it: there is no Open, no Pay, and no button anywhere on this
 * screen that hands a payload to another app. The decoded content is shown as inert text so
 * the user can see exactly what the code really contained.
 */
@Composable
fun QrScanScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrScanViewModel = hiltViewModel(),
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

                state.stage == QrStage.RESULT -> QrResult(
                    state = state,
                    onRescan = viewModel::rescan,
                )

                else -> ScannerPane(
                    isWorking = state.stage == QrStage.WORKING,
                    onPayload = viewModel::onPayloadDetected,
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
            text = "Scan QR reads a code through the camera and investigates what it " +
                "contains. Frames stay on this device and are never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (wasDenied) {
            Text(
                text = "Permission was declined. You can still use Check URL or Check " +
                    "Message, or grant camera access in Settings.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

@Composable
private fun ScannerPane(
    isWorking: Boolean,
    onPayload: (String, Long) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Held via rememberUpdatedState so the long-lived controller always calls the current
    // lambdas, and a recomposition never causes the camera to be rebound.
    val currentOnPayload by rememberUpdatedState(onPayload)
    val currentOnError by rememberUpdatedState(onError)

    val controller = remember(lifecycleOwner) {
        QrCameraController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onPayload = { currentOnPayload(it, System.currentTimeMillis()) },
            onError = { currentOnError(it) },
        )
    }

    // Release the camera, the decoder and the analysis thread when the screen goes away.
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scan QR", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Point at a QR code. It is decoded on this device and investigated — " +
                "never opened, and never paid.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller.start(view)
                    }
                },
            )
            if (isWorking) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        Text(
            text = if (isWorking) "Investigating…" else "Searching for a code…",
            modifier = Modifier.padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun QrResult(state: QrScanUiState, onRescan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("QR code detected", style = MaterialTheme.typography.headlineSmall)

        state.cameraError?.let { Notice("Camera problem: $it") }

        when (val outcome = state.outcome) {
            is QrScanOutcome.Analysed -> {
                PayloadCard(outcome.payload)
                Notice(
                    "Decoded on this device. TrustLens has not opened this code, contacted " +
                        "anything in it, or started any payment.",
                )
                RiskResultView(result = outcome.result)
            }

            QrScanOutcome.Empty -> EmptyGuidance()

            null -> Unit
        }

        Button(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text("Scan another code")
        }
    }
}

/**
 * What the code actually contained, shown before the verdict.
 *
 * Everything here is inert text. A URL is not a link, a payee is not a payment target, and
 * there is deliberately no action attached to any of it.
 */
@Composable
private fun PayloadCard(payload: QrPayload) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field("Type", typeLabel(payload))
            HorizontalDivider()

            when (payload) {
                is QrPayload.Url -> Field("Link", payload.url)
                is QrPayload.Text -> Field("Content", payload.raw)
                is QrPayload.Upi -> UpiFields(payload.request)
                QrPayload.Empty -> Unit
            }
        }
    }
}

@Composable
private fun UpiFields(request: UpiPaymentRequest) {
    Field("Payee", request.payeeAddress ?: "not stated in the code")
    request.payeeName?.let {
        // Named by whoever created the code, and verified by nobody.
        Field("Claimed name", "$it (unverified)")
    }
    request.amount?.let {
        Field("Amount", listOfNotNull(request.currency, it).joinToString(" "))
    }
    request.merchantCode?.let { Field("Merchant code", it) }
    request.transactionNote?.let { Field("Note", it) }
    request.url?.let { Field("Link inside the code", it) }
}

@Composable
private fun Field(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun typeLabel(payload: QrPayload): String = when (payload) {
    is QrPayload.Url -> "Link"
    is QrPayload.Upi -> "UPI payment request"
    is QrPayload.Text -> "Text"
    QrPayload.Empty -> "Empty"
}

@Composable
private fun EmptyGuidance() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "The code decoded to nothing.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("No assessment was made, because there was nothing to assess.")
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
