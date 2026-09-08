package com.trustlens.feature.textcheck

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustlens.core.design.Corner
import com.trustlens.core.design.LoadingState
import com.trustlens.core.design.NoticeCard
import com.trustlens.core.design.PrimaryButton
import com.trustlens.core.design.ScreenHeader
import com.trustlens.core.design.Space
import com.trustlens.core.design.StatusChip
import com.trustlens.core.design.TrustLens
import com.trustlens.feature.result.RiskResultView

/**
 * The Check Message flow.
 *
 * Everything shown after "Analyse" comes from the real pipeline — extractor, classifier and
 * deterministic risk engine. Nothing on this screen is hard-coded or illustrative.
 *
 * It is deliberately not conversational. TrustLens is an analyser, not a chat partner: one
 * input, one verdict, and the verdict stays on screen to be read rather than scrolling away
 * under a reply.
 */
@Composable
fun TextCheckScreen(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: TextCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TextCheckContent(
        state = state,
        onMessageChange = viewModel::onMessageChange,
        onAnalyse = viewModel::analyse,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun TextCheckContent(
    state: TextCheckUiState,
    onMessageChange: (String) -> Unit,
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
                title = "Check a message",
                onBack = onBack,
                subtitle = if (state.fromSelection) {
                    "TrustLens analysed the text you highlighted, on this device."
                } else {
                    "Paste the message below. TrustLens analyses it on this device."
                },
                trailing = {
                    // Stated as a fact about where this text came from, not as a mode the
                    // user chose. It is also the first thing a screen reader reaches.
                    if (state.fromSelection) StatusChip("Selected text")
                },
            )

            /*
             * Said before the verdict, and worded to close off the wrong inference.
             *
             * Arriving from Gmail's selection menu could easily read as "TrustLens can see my
             * mail". It cannot: `ACTION_PROCESS_TEXT` hands over the highlighted characters
             * and nothing else, and TrustLens holds no mailbox, account or message permission
             * of any kind.
             */
            if (state.fromSelection) {
                NoticeCard(
                    "Analysing selected text only. TrustLens received just the text you " +
                        "highlighted — it has no access to your mailbox, your messages, " +
                        "or anything else in that app.",
                )
            }

            // Truncation is stated, never silent: a link dropped off the end of a long
            // selection would otherwise look like a link that was checked and found clean.
            state.selectionOriginalLength?.let { original ->
                NoticeCard(
                    "That selection was $original characters. Only the first " +
                        "${state.message.length} were analysed, so anything after that has " +
                        "not been checked.",
                )
            }

            OutlinedTextField(
                value = state.message,
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                label = { Text("Paste message here") },
                enabled = !state.isAnalysing,
                shape = Corner.inner,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TrustLens.colors.surface,
                    unfocusedContainerColor = TrustLens.colors.surface,
                    disabledContainerColor = TrustLens.colors.surface,
                ),
            )

            PrimaryButton(
                text = if (state.isAnalysing) "Analysing…" else "Analyse",
                onClick = onAnalyse,
                enabled = state.canAnalyse,
            )

            if (state.isAnalysing) {
                LoadingState()
            }

            state.result?.let { result ->
                RiskResultView(result = result, modifier = Modifier.padding(top = Space.sm))
            }
        }
    }
}
