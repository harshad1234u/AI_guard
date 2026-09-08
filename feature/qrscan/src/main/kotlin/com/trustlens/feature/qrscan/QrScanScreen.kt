package com.trustlens.feature.qrscan

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.trustlens.core.design.AlertCard
import com.trustlens.core.design.Corner
import com.trustlens.core.design.EmptyState
import com.trustlens.core.design.ErrorState
import com.trustlens.core.design.FieldRow
import com.trustlens.core.design.LoadingState
import com.trustlens.core.design.NoticeCard
import com.trustlens.core.design.PrimaryButton
import com.trustlens.core.design.SectionHeader
import com.trustlens.core.design.ScreenHeader
import com.trustlens.core.design.SecondaryButton
import com.trustlens.core.design.Space
import com.trustlens.core.design.StatusChip
import com.trustlens.core.design.TrustLens
import com.trustlens.core.design.TrustLensCard
import com.trustlens.domain.QrPayload
import com.trustlens.domain.QrScanOutcome
import com.trustlens.domain.SafeAction
import com.trustlens.domain.UpiPaymentRequest
import com.trustlens.feature.result.RiskResultView

/**
 * Scan QR.
 *
 * Points the camera at a code, decodes it on-device, and investigates the payload. What the
 * screen never does is act on it: there is no Open, no Pay, and no button anywhere on this
 * screen that hands a payload to another app. The decoded content is shown as inert text so
 * the user can see exactly what the code really contained.
 *
 * The reticle is a square with corner brackets, deliberately unlike Lens Scan's wide text
 * guide — the two camera screens do different jobs and must not invite the same framing.
 */
@Composable
fun QrScanScreen(
    onBack: (() -> Unit)?,
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
                title = "Scan a QR code",
                onBack = onBack,
                subtitle = "Decoded and investigated — never opened, never paid.",
                modifier = Modifier.padding(top = Space.sm, bottom = Space.md),
            )

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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = "Camera access needed",
            style = MaterialTheme.typography.titleLarge,
            color = TrustLens.colors.text,
        )
        Text(
            text = "Scan QR reads a code through the camera and investigates what it " +
                "contains. Frames stay on this device and are never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = TrustLens.colors.textMuted,
        )
        if (wasDenied) {
            NoticeCard(
                "Permission was declined. You can still use Check URL or Check Message, " +
                    "or grant camera access in Settings.",
            )
        }
        PrimaryButton(text = "Allow camera", onClick = onRequest)
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
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = "Decoding a code is not the same as acting on it. TrustLens reads what " +
                "the code contains and stops there.",
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
                        controller.start(view)
                    }
                },
            )

            // A square reticle: a QR code is square, and this is what tells the user at a
            // glance that they are not on the text scanner.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .border(2.dp, TrustLens.colors.surface.copy(alpha = 0.85f), Corner.card),
            )

            if (isWorking) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TrustLens.colors.screen.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingState()
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(bottom = Space.lg), Alignment.Center) {
            StatusChip(if (isWorking) "Investigating…" else "Searching for a code…")
        }
    }
}

@Composable
private fun QrResult(state: QrScanUiState, onRescan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        state.cameraError?.let { ErrorState(title = "Camera problem", body = it) }

        when (val outcome = state.outcome) {
            is QrScanOutcome.Analysed -> {
                /*
                 * The payment refusal, before anything else on the screen.
                 *
                 * A UPI code the engine flagged as one not to pay is the one case where the
                 * user may already have their thumb over a payment app, so DO NOT PAY is
                 * hoisted above the payload and the verdict rather than being left to appear
                 * in the safe-action list further down. The action itself still comes from the
                 * engine — this only changes where it is drawn.
                 */
                if (SafeAction.DO_NOT_PAY in outcome.result.risk.recommendedActions) {
                    AlertCard(
                        title = "DO NOT PAY",
                        body = "This code asks for a payment and TrustLens found reasons not " +
                            "to trust it. Nothing has been paid — TrustLens cannot start a " +
                            "payment, and this screen has no button that would.",
                    )
                }

                SectionHeader("What the code contained")
                PayloadCard(outcome.payload)

                NoticeCard(
                    "Decoded on this device. TrustLens has not opened this code, contacted " +
                        "anything in it, or started any payment.",
                )
                RiskResultView(result = outcome.result)
            }

            QrScanOutcome.Empty -> EmptyState(
                title = "The code decoded to nothing",
                body = "No assessment was made, because there was nothing to assess.",
            )

            null -> Unit
        }

        SecondaryButton(
            text = "Scan another code",
            onClick = onRescan,
            modifier = Modifier.padding(vertical = Space.lg),
        )
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
    TrustLensCard(modifier = Modifier.fillMaxWidth()) {
        StatusChip(typeLabel(payload))

        when (payload) {
            is QrPayload.Url -> FieldRow("Link", payload.url)
            is QrPayload.Text -> FieldRow("Content", payload.raw)
            is QrPayload.Upi -> UpiFields(payload.request)
            QrPayload.Empty -> Unit
        }
    }
}

@Composable
private fun UpiFields(request: UpiPaymentRequest) {
    FieldRow("Payee", request.payeeAddress ?: "not stated in the code")
    request.payeeName?.let {
        // Named by whoever created the code, and verified by nobody.
        FieldRow("Claimed name", "$it (unverified)")
    }
    request.amount?.let {
        FieldRow("Amount", listOfNotNull(request.currency, it).joinToString(" "))
    }
    request.merchantCode?.let { FieldRow("Merchant code", it) }
    request.transactionNote?.let { FieldRow("Note", it) }
    request.url?.let { FieldRow("Link inside the code", it) }
}

private fun typeLabel(payload: QrPayload): String = when (payload) {
    is QrPayload.Url -> "Link"
    is QrPayload.Upi -> "UPI payment request"
    is QrPayload.Text -> "Text"
    QrPayload.Empty -> "Empty"
}
