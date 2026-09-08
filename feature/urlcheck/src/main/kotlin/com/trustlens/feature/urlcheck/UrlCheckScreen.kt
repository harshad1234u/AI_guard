package com.trustlens.feature.urlcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustlens.core.design.BulletRow
import com.trustlens.core.design.Corner
import com.trustlens.core.design.FieldRow
import com.trustlens.core.design.LoadingState
import com.trustlens.core.design.NoticeCard
import com.trustlens.core.design.PrimaryButton
import com.trustlens.core.design.ScreenHeader
import com.trustlens.core.design.SectionHeader
import com.trustlens.core.design.Space
import com.trustlens.core.design.TrustLens
import com.trustlens.core.design.TrustLensCard
import com.trustlens.core.design.UnknownState
import com.trustlens.domain.UrlAnalysis
import com.trustlens.domain.UrlFinding
import com.trustlens.feature.result.RiskResultView

/**
 * Check URL.
 *
 * The screen's promise, and the reason it has no Open button anywhere on it: **the link is
 * analysed without ever being opened.** Structure is parsed offline; reputation providers are
 * asked about the hostname alone. A screen whose purpose is warning someone about a link must
 * not also be the easiest way to visit it.
 */
@Composable
fun UrlCheckScreen(
    onBack: (() -> Unit)?,
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
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
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
                title = "Check a link",
                onBack = onBack,
                subtitle = "Link analysed without opening it.",
            )

            NoticeCard(
                "TrustLens inspects the link's structure on this device. It never opens, " +
                    "visits, expands or follows it. Reputation providers are asked about the " +
                    "hostname only — never the path or the query.",
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste link here") },
                singleLine = false,
                shape = Corner.inner,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TrustLens.colors.surface,
                    unfocusedContainerColor = TrustLens.colors.surface,
                ),
            )

            PrimaryButton(
                text = if (state.isAnalysing) "Analysing…" else "Analyse link",
                onClick = onAnalyse,
                enabled = state.canAnalyse,
            )

            if (state.isUnparseable) {
                UnknownState(
                    title = "That text could not be read as a link",
                    body = "Nothing was analysed, so nothing here says anything about it " +
                        "either way. Check the address and try again.",
                )
            }

            if (state.isAnalysing) {
                LoadingState()
            }

            state.analysis?.takeIf { it.isParseable }?.let { UrlStructureSection(it) }

            state.result?.let { result ->
                RiskResultView(result = result, modifier = Modifier.padding(top = Space.sm))
            }
        }
    }
}

/**
 * Structural detail for the inspected link, shown before the verdict.
 *
 * Everything here is rendered as plain text. There is deliberately no clickable affordance
 * anywhere in this section — not on the link, not on the host, not on the domain.
 */
@Composable
private fun UrlStructureSection(analysis: UrlAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        SectionHeader("Structural analysis")
        Text(
            text = "Taken apart on this device. Not opened, not resolved, not fetched.",
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.textMuted,
        )

        TrustLensCard(modifier = Modifier.fillMaxWidth()) {
            FieldRow("Link", analysis.normalised)
            analysis.host?.let { FieldRow("Host", it) }
            analysis.registrableDomain?.let { FieldRow("Domain", it) }
            analysis.scheme?.let { FieldRow("Scheme", it) }
            analysis.port?.let { FieldRow("Port", it.toString()) }
            if (analysis.subdomainLabels.isNotEmpty()) {
                FieldRow("Subdomains", analysis.subdomainLabels.joinToString("."))
            }
        }

        if (analysis.findings.isNotEmpty()) {
            TrustLensCard(
                modifier = Modifier.fillMaxWidth(),
                background = TrustLens.colors.screen,
                shape = Corner.inner,
            ) {
                Text(
                    text = "DETECTED IN THE STRUCTURE",
                    style = MaterialTheme.typography.labelLarge,
                    color = TrustLens.colors.textMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                analysis.findings.sortedBy { it.ordinal }.forEach { BulletRow(describe(it)) }
            }
        }
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
