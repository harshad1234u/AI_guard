package com.trustlens.feature.voicecheck

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text("Voice Check", style = MaterialTheme.typography.headlineSmall)

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

                VoiceStage.WORKING -> WorkingPane()

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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Disclosure happens *before* the microphone is ever used, not after.
    Notice(backendDisclosure(backend))

    if (permissionDenied) {
        Notice(
            "Microphone permission was declined. Voice Check needs it to listen, and " +
                "nothing else in TrustLens does. You can grant it in Settings, or type the " +
                "message below instead.",
        )
    }

    if (backend != null) {
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start listening")
        }
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
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "The microphone is on. It stops when you press Stop, when speech ends, or " +
            "when you leave this screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (partial.isNotBlank()) {
        // Interim words only, so the user can see it is working. Not analysed.
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "\"$partial\"",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
}

@Composable
private fun WorkingPane() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Analysing…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ResultPane(
    outcome: VoiceCheckOutcome?,
    onReset: () -> Unit,
    onManualText: (String) -> Unit,
) {
    when (outcome) {
        is VoiceCheckOutcome.Analysed -> {
            Notice(
                "Source: ${backendLabel(outcome.backend)}. The words below were analysed " +
                    "exactly like a pasted message — speech itself is not treated as a " +
                    "risk signal.",
            )
            if (outcome.isPartial) {
                Notice(
                    "Only a few words were recognised, so this assessment is based on " +
                        "partial content. Consider listening again.",
                )
            }

            RiskResultView(result = outcome.result)

            TranscriptCard(outcome.transcript)
        }

        VoiceCheckOutcome.NoSpeech -> Guidance(
            title = "No speech was recognised.",
            body = "No assessment was made, because nothing was heard.",
            hints = listOf(
                "Speak a little closer to the phone",
                "Move somewhere quieter",
                "Start listening again and speak right away",
            ),
        )

        is VoiceCheckOutcome.Unavailable -> Guidance(
            title = "Voice recognition unavailable.",
            body = "${outcome.reason}. You can paste or type the message instead.",
            hints = emptyList(),
        )

        is VoiceCheckOutcome.Failed -> Guidance(
            title = "Could not complete the recognition.",
            body = outcome.reason,
            hints = emptyList(),
        )

        null -> Unit
    }

    if (outcome !is VoiceCheckOutcome.Analysed) {
        ManualEntry(prompt = "Type or paste what was said", onSubmit = onManualText)
    }

    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        Text("Start over")
    }
}

@Composable
private fun ManualEntry(prompt: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(prompt) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onSubmit(text) },
        enabled = text.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Analyse this text")
    }
}

/**
 * What was actually heard, shown so the user can judge the recognition for themselves —
 * speech recognisers mishear, and a verdict on the wrong words is worth knowing about.
 * Rendered as inert text; links inside it are not clickable.
 */
@Composable
private fun TranscriptCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "\"$text\"", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Guidance(title: String, body: String, hints: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body)
        hints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
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

private fun backendLabel(backend: TranscriptionBackend): String = when (backend) {
    TranscriptionBackend.ON_DEVICE -> "on-device speech recognition"
    TranscriptionBackend.SYSTEM_MAY_USE_NETWORK -> "the system recogniser (may use network)"
    TranscriptionBackend.MANUAL_ENTRY -> "text you typed"
}

private fun backendDisclosure(backend: TranscriptionBackend?): String = when (backend) {
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
