package com.trustlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.trustlens.feature.home.HomeScreen
import com.trustlens.feature.lensscan.LensScanScreen
import com.trustlens.feature.qrscan.QrScanScreen
import com.trustlens.feature.textcheck.TextCheckScreen
import com.trustlens.feature.urlcheck.UrlCheckScreen
import com.trustlens.feature.voicecheck.VoiceCheckScreen
import com.trustlens.ui.TrustLensTheme
import dagger.hilt.android.AndroidEntryPoint

/** Every input path TrustLens offers. */
private enum class Screen {
    HOME,
    TEXT_CHECK,
    URL_CHECK,
    LENS_SCAN,
    QR_SCAN,
    VOICE_CHECK,
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrustLensTheme {
                TrustLensApp()
            }
        }
    }
}

/**
 * A plain state switch rather than a navigation library: with two destinations, adding a
 * navigation dependency would cost more than it gives.
 */
@Composable
private fun TrustLensApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    when (screen) {
        Screen.HOME -> HomeScreen(
            onCheckMessage = { screen = Screen.TEXT_CHECK },
            onCheckUrl = { screen = Screen.URL_CHECK },
            onScanWithCamera = { screen = Screen.LENS_SCAN },
            onScanQr = { screen = Screen.QR_SCAN },
            onVoiceCheck = { screen = Screen.VOICE_CHECK },
        )
        Screen.TEXT_CHECK -> TextCheckScreen(onBack = { screen = Screen.HOME })
        Screen.URL_CHECK -> UrlCheckScreen(onBack = { screen = Screen.HOME })
        Screen.LENS_SCAN -> LensScanScreen(onBack = { screen = Screen.HOME })
        Screen.QR_SCAN -> QrScanScreen(onBack = { screen = Screen.HOME })
        Screen.VOICE_CHECK -> VoiceCheckScreen(onBack = { screen = Screen.HOME })
    }
}
