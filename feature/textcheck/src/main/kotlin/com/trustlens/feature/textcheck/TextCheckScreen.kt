package com.trustlens.feature.textcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustlens.feature.result.RiskResultView

/**
 * The Check Message flow.
 *
 * Everything shown after "Analyse" comes from the real pipeline — extractor, classifier and
 * deterministic risk engine. Nothing on this screen is hard-coded or illustrative.
 */
@Composable
fun TextCheckScreen(
    onBack: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
            }

            Text(
                text = "Check a suspicious message",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Paste the message below. TrustLens analyses it on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.message,
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                label = { Text("Paste message here") },
                enabled = !state.isAnalysing,
            )

            Button(
                onClick = onAnalyse,
                enabled = state.canAnalyse,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isAnalysing) "Analysing…" else "Analyse")
            }

            if (state.isAnalysing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.result?.let { result ->
                RiskResultView(result = result, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
