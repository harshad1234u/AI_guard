package com.trustlens.feature.voicecheck

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
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
import com.trustlens.core.design.UnknownState
import com.trustlens.domain.TranscriptionBackend
import com.trustlens.domain.VoiceCheckOutcome
import com.trustlens.feature.result.RiskResultView

/**
 * Voice Check.
 *
 * The microphone runs only while the user holds this screen in a listening state, and the
 * words it produces are analysed by exactly the same pipeline as a pasted message. Nothing is
 * recorded, nothing is kept, and the screen always says which recogniser actually ran.
 */
@Composable
fun VoiceCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val transcriber = remember { SpeechTranscriber(context) }
    val backend = remember { transcriber.availableBackend() }

    // The microphone is released when this screen leaves composition, whatever the state.
    DisposableEffect(transcriber) {
        onDispose { transcriber.stop() }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    fun listen() {
        viewModel.onListeningStarted()
        transcriber.start(
            onPartial = viewModel::onPartialTranscript,
            onFinished = viewModel::onTranscription,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) listen()
    }

    // Asked for only when the user actually wants to listen — never on app start, and never
    // as a precondition for the rest of TrustLens.
    fun startListening() {
        if (hasPermission) listen() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TrustLens.colors.screen,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(start = Space.gutter, end = Space.gutter, top = Space.lg, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            ScreenHeader(
                title = "Voice Check",
                onBack = onBack,
                subtitle = "Say back what you heard, and have the words checked.",
            )

            when (state.stage) {
                VoiceStage.IDLE -> IdlePane(
                    backend = backend,
                    permissionDenied = permissionDenied,
                    onStart = ::startListening,
                    onManualText = viewModel::onManualText,
                )

                VoiceStage.LISTENING -> ListeningPane(
                    partial = state.partialTranscript,
                    onStop = { transcriber.stopListening() },
                )

                VoiceStage.WORKING -> LoadingState()

                VoiceStage.RESULT -> ResultPane(
                    outcome = state.outcome,
                    onReset = viewModel::reset,
                    onManualText = viewModel::onManualText,
                )
            }
        }
    }
}

@Composable
private fun IdlePane(
    backend: TranscriptionBackend?,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onManualText: (String) -> Unit,
) {
    Text(
        text = "Heard something suspicious on a call? Say it back, and TrustLens will " +
            "analyse the words the same way it analyses a message.",
        style = MaterialTheme.typography.bodyMedium,
        color = TrustLens.colors.textMuted,
    )

    // Disclosure happens *before* the microphone is ever used, not after.
    NoticeCard(backendDisclosure(backend))

    if (permissionDenied) {
        NoticeCard(
            "Microphone permission was declined. Voice Check needs it to listen, and " +
                "nothing else in TrustLens does. You can grant it in Settings, or type the " +
                "message below instead.",
        )
    }

    if (backend != null) {
        PrimaryButton(text = "Start listening", onClick = onStart)
    }

    ManualEntry(
        prompt = if (backend == null) {
            "Type or paste what was said"
        } else {
            "Or type it instead"
        },
        onSubmit = onManualText,
    )
}

@Composable
private fun ListeningPane(partial: String, onStop: () -> Unit) {
    Text(
        text = "Listening…",
        style = MaterialTheme.typography.titleLarge,
        color = TrustLens.colors.text,
    )
    Text(
        text = "The microphone is on. It stops when you press Stop, when speech ends, or " +
            "when you leave this screen.",
        style = MaterialTheme.typography.bodySmall,
        color = TrustLens.colors.textMuted,
    )

    if (partial.isNotBlank()) {
        // Interim words only, so the user can see it is working. Never analysed, and drawn
        // unlike the finished transcript so it cannot be mistaken for one.
        Text(
            text = "“$partial”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = TrustLens.colors.textMuted,
        )
    }

    PrimaryButton(text = "Stop", onClick = onStop)
}

@Composable
private fun ResultPane(
    outcome: VoiceCheckOutcome?,
    onReset: () -> Unit,
    onManualText: (String) -> Unit,
) {
    when (outcome) {
        is VoiceCheckOutcome.Analysed -> {
            NoticeCard(
                "Source: ${backendLabel(outcome.backend)}. The words below were analysed " +
                    "exactly like a pasted message — speech itself is not treated as a " +
                    "risk signal.",
            )
            if (outcome.isPartial) {
                NoticeCard(
                    "Only a few words were recognised, so this assessment is based on " +
                        "partial content. Consider listening again.",
                )
            }

            RiskResultView(result = outcome.result)

            QuotedContent(label = "Transcript", text = outcome.transcript)
        }

        VoiceCheckOutcome.NoSpeech -> EmptyState(
            title = "No speech was recognised",
            body = "No assessment was made, because nothing was heard. That is not the same " +
                "as finding nothing wrong.",
            hints = listOf(
                "Speak a little closer to the phone",
                "Move somewhere quieter",
                "Start listening again and speak right away",
            ),
        )

        is VoiceCheckOutcome.Unavailable -> UnknownState(
            title = "Voice recognition unavailable",
            body = "${outcome.reason}. You can paste or type the message instead — it is " +
                "analysed exactly the same way.",
        )

        is VoiceCheckOutcome.Failed -> ErrorState(
            title = "Could not complete the recognition",
            body = outcome.reason,
        )

        null -> Unit
    }

    if (outcome !is VoiceCheckOutcome.Analysed) {
        ManualEntry(prompt = "Type or paste what was said", onSubmit = onManualText)
    }

    SecondaryButton(
        text = "Start over",
        onClick = onReset,
        modifier = Modifier.padding(top = Space.sm),
    )
}

@Composable
private fun ManualEntry(prompt: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(prompt) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
        shape = Corner.inner,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TrustLens.colors.surface,
            unfocusedContainerColor = TrustLens.colors.surface,
        ),
    )
    PrimaryButton(
        text = "Analyse this text",
        onClick = { onSubmit(text) },
        enabled = text.isNotBlank(),
    )
}

internal fun backendLabel(backend: TranscriptionBackend): String = when (backend) {
    TranscriptionBackend.ON_DEVICE -> "on-device speech recognition"
    TranscriptionBackend.SYSTEM_MAY_USE_NETWORK -> "the system recogniser (may use network)"
    TranscriptionBackend.MANUAL_ENTRY -> "text you typed"
}

internal fun backendDisclosure(backend: TranscriptionBackend?): String = when (backend) {
    TranscriptionBackend.ON_DEVICE ->
        "This device supports on-device speech recognition. Your voice is transcribed " +
            "locally and no audio is uploaded or saved."

    TranscriptionBackend.SYSTEM_MAY_USE_NETWORK ->
        "This device has no on-device recogniser, so the system speech service will be used. " +
            "Depending on the service installed, your speech may be sent to Google for " +
            "processing. TrustLens itself never stores or uploads audio."

    TranscriptionBackend.MANUAL_ENTRY, null ->
        "No speech recognition service is available on this device. You can type or paste " +
            "what was said instead — it is analysed exactly the same way."
}
