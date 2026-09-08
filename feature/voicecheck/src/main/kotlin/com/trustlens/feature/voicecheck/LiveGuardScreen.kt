package com.trustlens.feature.voicecheck

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustlens.core.design.AlertCard
import com.trustlens.core.design.Corner
import com.trustlens.core.design.EmptyState
import com.trustlens.core.design.NoticeCard
import com.trustlens.core.design.PrimaryButton
import com.trustlens.core.design.QuotedContent
import com.trustlens.core.design.ReasonRow
import com.trustlens.core.design.RiskBadge
import com.trustlens.core.design.SafeActionCard
import com.trustlens.core.design.ScreenHeader
import com.trustlens.core.design.SecondaryButton
import com.trustlens.core.design.Space
import com.trustlens.core.design.TrustLens
import com.trustlens.core.design.TrustLensCard
import com.trustlens.core.design.riskColor
import com.trustlens.feature.result.RiskResultView

/**
 * Live Guard.
 *
 * Keeps listening through a conversation and re-scores what has been said so far, so a warning
 * can land while the call is still happening rather than afterwards.
 *
 * ## What this is, stated exactly
 *
 * This listens through the **microphone**. It is not attached to the call. Android does not
 * let an ordinary app read call audio — `VOICE_CALL` capture is a privileged permission — so
 * TrustLens hears the other party only when the call is on **speakerphone**, exactly as a
 * person in the room would. The screen says so before the microphone is ever used, and the
 * system microphone indicator is lit the entire time.
 *
 * There is no interception, no recording, no call handling and no background listening. The
 * words are analysed by the same pipeline a pasted message goes through.
 */
@Composable
fun LiveGuardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveGuardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val transcriber = remember { LiveTranscriber(context) }
    val backend = remember { SpeechTranscriber(context).availableBackend() }

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

    // Asked once, before listening, so a missing Hindi model is stated rather than
    // discovered as a suspiciously low score during a real scam call.
    var languages by remember { mutableStateOf(RecognitionLanguages.Support()) }
    var hindiRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        RecognitionLanguages.query(context) { languages = it }
    }

    fun listen() {
        viewModel.onListeningStarted(backend)
        transcriber.start(
            onSegment = viewModel::onSegment,
            onPartial = viewModel::onPartial,
            onLanguage = viewModel::onLanguageDetected,
            onStatus = { status ->
                when (status) {
                    LiveTranscriber.LiveStatus.Listening ->
                        viewModel.onHealth(ListeningHealth.OK)

                    LiveTranscriber.LiveStatus.NoAudio ->
                        viewModel.onHealth(ListeningHealth.NO_AUDIO)

                    is LiveTranscriber.LiveStatus.Failed ->
                        viewModel.onListeningEnded(status.reason)

                    is LiveTranscriber.LiveStatus.Unavailable ->
                        viewModel.onListeningEnded(status.reason)
                }
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) listen()
    }

    // Asked for only when the user actually wants to listen — never on entering the screen.
    fun startListening() {
        if (hasPermission) listen() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopListening() {
        transcriber.stop()
        viewModel.onListeningEnded()
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
                title = "Live Guard",
                onBack = onBack,
                subtitle = "Listens through the microphone, on speakerphone.",
                trailing = {
                    if (state.stage == LiveStage.RUNNING) {
                        ListeningPulse(healthy = state.health == ListeningHealth.OK)
                    }
                },
            )

            when (state.stage) {
                LiveStage.IDLE -> IdleGuardPane(
                    backend = backend,
                    languages = languages,
                    permissionDenied = permissionDenied,
                    canListen = backend != null,
                    onStart = ::startListening,
                    onInstallHindi = {
                        RecognitionLanguages.requestDownload(context, HINDI)
                        hindiRequested = true
                    },
                    hindiRequested = hindiRequested,
                )

                LiveStage.RUNNING -> RunningGuardPane(
                    state = state,
                    onStop = ::stopListening,
                )

                LiveStage.ENDED -> EndedGuardPane(
                    state = state,
                    onRestart = viewModel::reset,
                )
            }
        }
    }
}

@Composable
private fun IdleGuardPane(
    backend: com.trustlens.domain.TranscriptionBackend?,
    languages: RecognitionLanguages.Support,
    permissionDenied: Boolean,
    canListen: Boolean,
    hindiRequested: Boolean,
    onStart: () -> Unit,
    onInstallHindi: () -> Unit,
) {
    Text(
        text = "On a suspicious call, put it on speakerphone and start Live Guard. " +
            "TrustLens keeps reading the words as they are spoken and warns you the moment " +
            "the call starts asking for an OTP, a payment, or your account details.",
        style = MaterialTheme.typography.bodyMedium,
        color = TrustLens.colors.textMuted,
    )

    // The limit is stated up front, not discovered when it does not work.
    NoticeCard(
        "Live Guard listens through the microphone, like a person sitting next to you. " +
            "It is not connected to the call and cannot hear it unless the call is on " +
            "speakerphone. Android does not allow any ordinary app to read call audio.",
    )

    // Disclosure happens *before* the microphone is ever used, not after.
    NoticeCard(backendDisclosure(backend))

    /*
     * The language disclosure, and the reason it exists.
     *
     * A recogniser with no Hindi model does not refuse Hindi speech — it transcribes it as
     * English-sounding nonsense, which then scores as harmless. That failure is invisible
     * unless it is named, so it is named here rather than left to be discovered as a low
     * score during a real call.
     */
    when {
        !languages.queried -> NoticeCard(
            "Live Guard listens in whichever language this phone's speech recognition is " +
                "set to. Scam wording is detected in Hindi, Hinglish and English alike — " +
                "but only for speech the recogniser transcribes correctly in the first place.",
        )

        languages.isInstalled(HINDI) -> NoticeCard(
            "Hindi and English are both installed for offline recognition, and Live Guard " +
                "asks the recogniser to switch between them as the caller does. Scam wording " +
                "is detected in Hindi, Hinglish and English by the same rules.",
        )

        hindiRequested || languages.isPending(HINDI) -> NoticeCard(
            "The Hindi speech model is being downloaded by Android. Until it finishes, Hindi " +
                "speech may be transcribed inaccurately — and words TrustLens never receives " +
                "correctly cannot be checked.",
        )

        else -> NoticeCard(
            "Hindi speech model is not installed on this phone. Live Guard still detects " +
                "Hindi and Hinglish scam wording, but only in speech that is transcribed " +
                "correctly, so a Hindi call may be misheard and under-scored.",
        )
    }

    if (permissionDenied) {
        NoticeCard(
            "Microphone permission was declined. Live Guard needs it to listen, and nothing " +
                "else in TrustLens does. You can grant it in Settings, or use Check Message " +
                "to analyse what was said afterwards.",
        )
    }

    if (languages.isDownloadable(HINDI) && !hindiRequested && !languages.isPending(HINDI)) {
        SecondaryButton(
            text = "Install Hindi for offline recognition",
            onClick = onInstallHindi,
        )
    }

    if (canListen) {
        PrimaryButton(text = "Start Live Guard", onClick = onStart)
    }
}

/** The one language Live Guard treats specially, because its absence is the silent failure. */
private const val HINDI = "hi-IN"

/**
 * The listening indicator: a slow pulse while audio is arriving, and a still dot when it is
 * not.
 *
 * The stillness is deliberate. A pulse that kept animating through silence would say the
 * microphone is working when it is not, which is the exact failure §2 of the Phase 16 notes
 * calls the most dangerous one this feature could have.
 */
@Composable
private fun ListeningPulse(healthy: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val tone = if (healthy) TrustLens.colors.danger else TrustLens.colors.textMuted

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            Modifier
                .size(9.dp)
                .alpha(if (healthy) alpha else 0.4f)
                .clip(CircleShape)
                .background(tone)
                .clearAndSetSemantics { },
        )
        Text(
            text = if (healthy) "Listening" else "No audio",
            style = MaterialTheme.typography.labelMedium,
            color = tone,
        )
    }
}

@Composable
private fun RunningGuardPane(state: LiveGuardUiState, onStop: () -> Unit) {
    when (state.health) {
        ListeningHealth.OK -> Text(
            text = "The microphone is on. It stops when you press Stop or leave this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.textMuted,
        )

        // Silence must never be allowed to read as "nothing suspicious was said".
        ListeningHealth.NO_AUDIO -> AlertCard(
            title = "No audio is reaching TrustLens",
            body = "Nothing has been heard for a while. If you are on a call, turn on " +
                "speakerphone — Android gives the call itself priority over the " +
                "microphone. Nothing is being checked until audio arrives.",
        )
    }

    // Stated as what the recogniser reported, not as a claim about comprehension.
    state.detectedLanguage?.let {
        TrustLensCard(
            modifier = Modifier.fillMaxWidth(),
            background = TrustLens.colors.surface,
            shape = Corner.inner,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
        ) {
            Text(
                text = "DETECTED LANGUAGE",
                style = MaterialTheme.typography.labelLarge,
                color = TrustLens.colors.textMuted,
            )
            Text(
                text = "Recogniser reports: ${RecognitionLanguages.displayName(it)}",
                style = MaterialTheme.typography.bodyMedium,
                color = TrustLens.colors.text,
            )
        }
    }

    state.result?.let { LiveVerdictCard(state = state, result = it) }

    if (state.partial.isNotBlank()) {
        // Interim words. Visually unlike the analysed transcript below on purpose — italic,
        // quoted, muted, on no card — because these have *not* been scored, and a half-heard
        // sentence rendered like finished evidence would imply a verdict about words nobody
        // said.
        Text(
            text = "“${state.partial}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = TrustLens.colors.textMuted,
            modifier = Modifier.padding(horizontal = Space.xs),
        )
    }

    if (state.transcript.isNotBlank()) {
        QuotedContent(label = "Heard so far", text = state.transcript)
    }

    PrimaryButton(text = "Stop listening", onClick = onStop)
}

/**
 * The running verdict.
 *
 * Two numbers, deliberately. The live score is about the whole conversation so far; the peak
 * is the worst it ever got. A scammer says the dangerous sentence once, and a score that
 * quietly recovered while they changed the subject would be the wrong thing to leave in front
 * of the user.
 */
@Composable
private fun LiveVerdictCard(state: LiveGuardUiState, result: com.trustlens.domain.AnalysisResult) {
    val risk = result.risk
    val tone = riskColor(risk.level)

    TrustLensCard(
        modifier = Modifier.fillMaxWidth(),
        background = if (state.alerting) {
            TrustLens.colors.danger.copy(alpha = 0.12f)
        } else {
            tone.copy(alpha = 0.07f)
        },
        border = tone.copy(alpha = 0.32f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "CURRENT RISK",
                    style = MaterialTheme.typography.labelLarge,
                    color = TrustLens.colors.textMuted,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = risk.score.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = tone,
                    )
                    Text(
                        text = "/100  ${risk.level.name}",
                        style = MaterialTheme.typography.titleSmall,
                        color = tone,
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                    )
                }
            }

            if (state.peakLevel != null && state.peakScore > risk.score) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "HIGHEST SO FAR",
                        style = MaterialTheme.typography.labelLarge,
                        color = TrustLens.colors.textMuted,
                    )
                    RiskBadge(level = state.peakLevel!!, score = state.peakScore)
                }
            }
        }

        if (state.alerting) {
            Text(
                text = risk.level.userAction,
                style = MaterialTheme.typography.titleMedium,
                color = TrustLens.colors.text,
            )
        }

        risk.reasons.take(MAX_LIVE_REASONS).forEach { ReasonRow(it) }

        SafeActionCard(
            actions = risk.recommendedActions.take(MAX_LIVE_ACTIONS),
            level = risk.level,
            compact = true,
        )
    }
}

@Composable
private fun EndedGuardPane(state: LiveGuardUiState, onRestart: () -> Unit) {
    state.problem?.let { AlertCard(title = "Listening stopped", body = it) }

    if (state.peakLevel != null && state.result != null &&
        state.peakScore > state.result!!.risk.score
    ) {
        AlertCard(
            title = "Highest risk during this call: ${state.peakScore}/100 " +
                "(${state.peakLevel!!.name})",
            body = "The score below is for everything that was said. The figure above is the " +
                "worst single point the conversation reached.",
        )
    }

    when {
        state.result != null -> {
            NoticeCard(
                "Source: Live Guard. The words below were heard through the microphone and " +
                    "analysed exactly like a pasted message.",
            )
            RiskResultView(result = state.result!!)
            QuotedContent(label = "Transcript", text = state.transcript)
        }

        state.transcript.isBlank() -> EmptyState(
            title = "Nothing was heard",
            body = "Nothing was assessed. That is not the same as finding nothing wrong.",
        )

        else -> EmptyState(
            title = "Listening ended before an assessment could be made",
            body = "The words heard so far were not scored, so nothing here says anything " +
                "about the call either way.",
        )
    }

    SecondaryButton(text = "Start over", onClick = onRestart)
}

/** Enough to justify the warning without turning the live card into a wall of text. */
private const val MAX_LIVE_REASONS = 4
private const val MAX_LIVE_ACTIONS = 2
