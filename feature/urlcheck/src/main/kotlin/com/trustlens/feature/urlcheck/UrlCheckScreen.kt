package com.trustlens.feature.urlcheck

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustlens.domain.UrlAnalysis
import com.trustlens.domain.UrlFinding
import com.trustlens.feature.result.RiskResultView

@Composable
fun UrlCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UrlCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    UrlCheckContent(
        state = state,
        onUrlChange = viewModel::onUrlChange,
        onAnalyse = viewModel::analyse,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun UrlCheckContent(
    state: UrlCheckUiState,
    onUrlChange: (String) -> Unit,
    onAnalyse: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("Back") }
            }

            Text("Check a link", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "TrustLens inspects the link's structure on this device. " +
                    "It never opens, visits or expands it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste link here") },
                singleLine = false,
            )

            Button(
                onClick = onAnalyse,
                enabled = state.canAnalyse,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Analyse link")
            }

            if (state.isUnparseable) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = "That text could not be read as a link.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.analysis?.takeIf { it.isParseable }?.let { UrlDetailCard(it) }

            state.result?.let { result ->
                RiskResultView(result = result, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * Structural detail for the inspected link.
 *
 * Everything here is rendered as plain text. There is deliberately no clickable affordance:
 * a screen whose purpose is warning the user about a link must not also be the easiest way to
 * open it.
 */
@Composable
private fun UrlDetailCard(analysis: UrlAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Local link analysis",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Structure examined on this device. Reputation providers are asked " +
                    "about the hostname only — the link itself is never opened or visited.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            DetailRow("Link", analysis.normalised)
            analysis.host?.let { DetailRow("Host", it) }
            analysis.registrableDomain?.let { DetailRow("Domain", it) }
            analysis.scheme?.let { DetailRow("Scheme", it) }
            analysis.port?.let { DetailRow("Port", it.toString()) }
            if (analysis.subdomainLabels.isNotEmpty()) {
                DetailRow("Subdomains", analysis.subdomainLabels.joinToString("."))
            }

            if (analysis.findings.isNotEmpty()) {
                Text(
                    text = "Detected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                analysis.findings.sortedBy { it.ordinal }.forEach {
                    Text("• ${describe(it)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun describe(finding: UrlFinding): String = when (finding) {
    UrlFinding.IP_LITERAL_HOST -> "Uses a raw IP address instead of a site name"
    UrlFinding.PRIVATE_IP_HOST -> "Address is on a private network (noted, not scored)"
    UrlFinding.SHORTENER -> "Uses a link shortener — the real destination is hidden"
    UrlFinding.PUNYCODE_LABEL -> "Uses a punycode (xn--) domain that can imitate a real name"
    UrlFinding.NON_ASCII_HOST -> "Hostname contains non-standard characters"
    UrlFinding.EXCESSIVE_SUBDOMAINS -> "Unusually deep subdomains"
    UrlFinding.EMBEDDED_CREDENTIALS -> "Hides its real destination behind an @ sign"
    UrlFinding.EXECUTABLE_DOWNLOAD_PATH -> "Points at an installable file"
    UrlFinding.NON_STANDARD_PORT -> "Uses a non-standard port"
    UrlFinding.INSECURE_SCHEME -> "Uses plain HTTP (noted, not scored)"
}
