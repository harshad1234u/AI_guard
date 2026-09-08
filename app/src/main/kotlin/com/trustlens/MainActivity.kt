package com.trustlens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.trustlens.core.design.Glyph
import com.trustlens.core.design.TrustLens
import com.trustlens.core.design.TrustLensBottomBar
import com.trustlens.core.design.TrustLensTheme
import com.trustlens.feature.emailshare.EmailCheckScreen
import com.trustlens.feature.emailshare.EmailCheckViewModel
import com.trustlens.feature.home.HomeScreen
import com.trustlens.feature.lensscan.LensScanScreen
import com.trustlens.feature.qrscan.QrScanScreen
import com.trustlens.feature.textcheck.TextCheckScreen
import com.trustlens.feature.textcheck.TextCheckViewModel
import com.trustlens.feature.urlcheck.UrlCheckScreen
import com.trustlens.feature.voicecheck.LiveGuardScreen
import com.trustlens.feature.voicecheck.VoiceCheckScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Every input path TrustLens offers.
 *
 * The four in [TOP_LEVEL] are reachable from the bottom bar. The rest are *layers* opened
 * from Home: they cover the bar and offer Back, which is what keeps seven surfaces reading as
 * one product rather than as seven separate applications.
 */
private enum class Screen(val label: String, val glyph: Glyph) {
    HOME("Home", Glyph.HOME),
    TEXT_CHECK("Message", Glyph.MESSAGE),
    LENS_SCAN("Scan", Glyph.CAMERA),
    QR_SCAN("QR", Glyph.QR),
    URL_CHECK("Link", Glyph.LINK),
    VOICE_CHECK("Voice", Glyph.VOICE),
    LIVE_GUARD("Live Guard", Glyph.LIVE),
    EMAIL_CHECK("Email", Glyph.EMAIL),
}

private val TOP_LEVEL = listOf(Screen.HOME, Screen.TEXT_CHECK, Screen.LENS_SCAN, Screen.QR_SCAN)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Leaves the startup screen behind. Theme.TrustLens.Splash is the window until this
        // line runs, so the mark is shown for exactly as long as starting up actually takes.
        setTheme(R.style.Theme_TrustLens)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        render(incomingTextFrom(intent))
    }

    /** Handles a share or a selection that arrives while this instance is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(incomingTextFrom(intent))
    }

    private fun render(incoming: IncomingText?) {
        setContent {
            TrustLensTheme {
                TrustLensApp(incoming = incoming)
            }
        }
    }
}

/**
 * Reads the two intents TrustLens accepts.
 *
 * Both are *input*: another app hands TrustLens some text to look at. This activity still
 * starts no activity of its own — there is no `startActivity`, no `ACTION_VIEW` and no
 * `WebView` anywhere in this file or the app — so content comes in and only a verdict goes
 * back to the screen. Principle 3 is untouched by either entry point.
 *
 * `ACTION_PROCESS_TEXT` never has its result returned. TrustLens is a read-only processor: it
 * does not modify, replace or write back the text the user highlighted, so `setResult` is
 * deliberately never called.
 */
private fun incomingTextFrom(intent: Intent?): IncomingText? {
    if (intent == null) return null
    return IncomingTextReader.read(
        action = intent.action,
        subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
        sendText = intent.getStringExtra(Intent.EXTRA_TEXT),
        processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
    )
}

/**
 * A plain state switch rather than a navigation library: with a handful of destinations,
 * adding a navigation dependency would still cost more than it gives.
 *
 * The shell is one layer of chrome — the bottom bar — with the current screen above it. The
 * bar shows only on the four top-level destinations; a screen opened from Home covers it and
 * is dismissed with Back, so the user is never one careless tap from losing an analysis they
 * are reading.
 *
 * Where incoming text lands is a deliberate distinction, and it follows what the sending app
 * actually supplied rather than what it might have been. A share carrying a *subject* goes to
 * Check Email, which can show a sender. A share carrying only text, and a highlighted
 * *fragment*, both go to Check Message — neither is an email, and presenting either inside a
 * mail-shaped screen would imply TrustLens saw more than the characters it was handed.
 */
@Composable
private fun TrustLensApp(incoming: IncomingText? = null) {
    var screen by remember(incoming) {
        mutableStateOf(
            when (incoming) {
                is IncomingText.Shared -> Screen.EMAIL_CHECK
                is IncomingText.Message -> Screen.TEXT_CHECK
                is IncomingText.Selection -> Screen.TEXT_CHECK
                null -> Screen.HOME
            },
        )
    }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    val showBar = screen in TOP_LEVEL

    Column(
        Modifier
            .fillMaxSize()
            .background(TrustLens.colors.screen),
    ) {
        Box(
            Modifier
                .weight(1f)
                // With the bar present it, not the screen, owns the gesture inset — otherwise
                // both would pad for it and leave a gap above the bar.
                .then(
                    if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier,
                ),
        ) {
            Destination(screen = screen, incoming = incoming, onNavigate = { screen = it })
        }

        if (showBar) {
            TrustLensBottomBar(
                items = TOP_LEVEL,
                selected = screen,
                onSelect = { screen = it },
                label = { it.label },
                glyph = { it.glyph },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun Destination(
    screen: Screen,
    incoming: IncomingText?,
    onNavigate: (Screen) -> Unit,
) {
    val home = { onNavigate(Screen.HOME) }

    when (screen) {
        Screen.HOME -> HomeScreen(
            onCheckMessage = { onNavigate(Screen.TEXT_CHECK) },
            onCheckUrl = { onNavigate(Screen.URL_CHECK) },
            onScanWithCamera = { onNavigate(Screen.LENS_SCAN) },
            onScanQr = { onNavigate(Screen.QR_SCAN) },
            onVoiceCheck = { onNavigate(Screen.VOICE_CHECK) },
            onLiveGuard = { onNavigate(Screen.LIVE_GUARD) },
            onCheckEmail = { onNavigate(Screen.EMAIL_CHECK) },
        )

        // The three tab destinations reached from the bar carry no Back control: the bar is
        // already on screen and is the way out.
        Screen.TEXT_CHECK -> TextCheckRoute(
            incoming = incoming,
            onBack = null,
        )
        Screen.LENS_SCAN -> LensScanScreen(onBack = null)
        Screen.QR_SCAN -> QrScanScreen(onBack = null)

        Screen.URL_CHECK -> UrlCheckScreen(onBack = home)
        Screen.VOICE_CHECK -> VoiceCheckScreen(onBack = home)
        Screen.LIVE_GUARD -> LiveGuardScreen(onBack = home)
        Screen.EMAIL_CHECK -> EmailShareRoute(
            share = incoming as? IncomingText.Shared,
            onBack = home,
        )
    }
}

/**
 * Check Message, prefilled and already analysed when the user arrived by sharing or selecting.
 *
 * The text is handed to the view model once rather than threaded through the screen on every
 * recomposition, and the same screen serves every entry point so a shared message, a
 * highlighted fragment and a pasted message reach an identical verdict through identical
 * code. Only the framing differs, and only because the two arrivals are different facts.
 */
@Composable
private fun TextCheckRoute(incoming: IncomingText?, onBack: (() -> Unit)?) {
    val viewModel: TextCheckViewModel = hiltViewModel()

    LaunchedEffect(incoming) {
        when (incoming) {
            is IncomingText.Message -> viewModel.onSharedText(incoming.text)
            is IncomingText.Selection ->
                viewModel.onSelectedText(incoming.text, incoming.originalLength)
            else -> Unit
        }
    }

    TextCheckScreen(onBack = onBack, viewModel = viewModel)
}

/**
 * Check Email, prefilled when the user arrived by sharing.
 *
 * The view model is resolved here so shared text can be handed to it once, rather than being
 * threaded through the screen's parameters on every recomposition.
 */
@Composable
private fun EmailShareRoute(share: IncomingText.Shared?, onBack: () -> Unit) {
    val viewModel: EmailCheckViewModel = hiltViewModel()

    LaunchedEffect(share) {
        share?.let { viewModel.onShared(it.subject, it.body) }
    }

    EmailCheckScreen(onBack = onBack, viewModel = viewModel)
}
