package com.trustlens.feature.emailshare

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
import com.trustlens.core.design.EmptyState
import com.trustlens.core.design.LoadingState
import com.trustlens.core.design.NoticeCard
import com.trustlens.core.design.PrimaryButton
import com.trustlens.core.design.ScreenHeader
import com.trustlens.core.design.SectionHeader
import com.trustlens.core.design.Space
import com.trustlens.core.design.StatusChip
import com.trustlens.core.design.TrustLens
import com.trustlens.core.design.TrustLensCard
import com.trustlens.domain.EmailCheckOutcome
import com.trustlens.feature.result.RiskResultView

/**
 * Check Email.
 *
 * A message shared in from any mail app — or pasted — goes through the same pipeline a pasted
 * SMS goes through. TrustLens has no mailbox access, no OAuth, and no account: it sees exactly
 * the one message the user chose to share, and nothing else.
 *
 * The screen is laid out as the three parts of an email — sender, subject, body — because
 * those are the three things a user reads when deciding whether a mail is genuine, and because
 * exactly one of them (the sender's *domain*) is the only thing on this screen that leaves the
 * device.
 */
@Composable
fun EmailCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmailCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                title = "Check an email",
                onBack = onBack,
                subtitle = "Analysing selected content only.",
                trailing = { StatusChip("No mailbox access") },
            )

            NoticeCard(
                "TrustLens cannot read your mailbox. It has no account access and sees only " +
                    "the single message you share. The subject and body never leave this " +
                    "device; if you supply a sender address, only its domain is checked " +
                    "against reputation providers.",
            )

            SectionHeader("The message")

            TrustLensCard(modifier = Modifier.fillMaxWidth()) {
                EmailField(
                    value = state.senderAddress,
                    onValueChange = viewModel::onSenderChanged,
                    label = "From",
                    supporting = "Only the part after the @ is looked up. Mail apps do not " +
                        "include the sender when sharing, so fill this in if it matters.",
                    singleLine = true,
                )

                EmailField(
                    value = state.subject,
                    onValueChange = viewModel::onSubjectChanged,
                    label = "Subject",
                    singleLine = true,
                )

                EmailField(
                    value = state.body,
                    onValueChange = viewModel::onBodyChanged,
                    label = "Body / selected content",
                    minHeight = 180.dp,
                )
            }

            PrimaryButton(
                text = if (state.stage == EmailStage.WORKING) "Analysing…" else "Analyse email",
                onClick = viewModel::analyse,
                enabled = state.canAnalyse && state.stage != EmailStage.WORKING,
            )

            if (state.stage == EmailStage.WORKING) {
                LoadingState()
            }

            when (val outcome = state.outcome) {
                is EmailCheckOutcome.Analysed -> {
                    NoticeCard(
                        "Source: email. The subject and body above were analysed exactly like " +
                            "a pasted message — there is no email-specific scoring.",
                    )

                    outcome.senderDomain?.let { domain ->
                        NoticeCard(
                            "Sender domain checked: $domain. A provider having no report for " +
                                "it is not a confirmation that it is genuine.",
                        )
                    }

                    RiskResultView(result = outcome.result)
                }

                EmailCheckOutcome.NothingToAnalyse -> EmptyState(
                    title = "There was nothing to analyse",
                    body = "That is not the same as finding nothing wrong — paste the " +
                        "email's wording and try again.",
                )

                null -> Unit
            }
        }
    }
}

/** One field of the email, labelled the way a mail client labels it. */
@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String? = null,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = TrustLens.colors.textMuted,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
            singleLine = singleLine,
            shape = Corner.inner,
            textStyle = MaterialTheme.typography.bodyLarge,
            supportingText = supporting?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TrustLens.colors.textMuted,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TrustLens.colors.screen,
                unfocusedContainerColor = TrustLens.colors.screen,
            ),
        )
    }
}
