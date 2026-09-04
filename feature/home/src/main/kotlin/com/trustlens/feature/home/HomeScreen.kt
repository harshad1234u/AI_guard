package com.trustlens.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * TrustLens home. Each entry maps to one phone-native input path; every path converges on the
 * same investigation pipeline, so this screen is a router rather than a feature list.
 */
@Composable
fun HomeScreen(
    onCheckMessage: () -> Unit = {},
    onCheckUrl: () -> Unit = {},
    onScanWithCamera: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onVoiceCheck: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "TrustLens",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "See the risk before you click.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            HomeAction.entries.forEach { action ->
                HomeActionCard(
                    action = action,
                    onClick = when (action) {
                        HomeAction.CHECK_MESSAGE -> onCheckMessage
                        HomeAction.CHECK_URL -> onCheckUrl
                        HomeAction.SCAN_CAMERA -> onScanWithCamera
                        HomeAction.SCAN_QR -> onScanQr
                        HomeAction.VOICE_CHECK -> onVoiceCheck
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeActionCard(action: HomeAction, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(action.title, style = MaterialTheme.typography.titleMedium)
            Text(
                action.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal enum class HomeAction(val title: String, val subtitle: String) {
    CHECK_MESSAGE("Check Message", "Paste or share a suspicious SMS or text"),
    SCAN_CAMERA("Scan with Camera", "Point at a message, notice or webpage"),
    SCAN_QR("Scan QR", "Decode a payment or link QR without opening it"),
    CHECK_URL("Check URL", "Investigate a link before you visit it"),
    VOICE_CHECK("Voice Check", "Analyse a suspicious claim you heard"),
}

@Preview
@Composable
private fun HomeScreenPreview() {
    MaterialTheme { HomeScreen() }
}
