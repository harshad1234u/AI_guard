package com.trustlens.feature.emailshare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trustlens.domain.AnalyzeEmailUseCase
import com.trustlens.domain.EmailCheckOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Check Email screen is currently showing. */
enum class EmailStage {
    /** Waiting for content — either a share from a mail app, or pasted text. */
    INPUT,

    /** Analysis is running. */
    WORKING,

    /** A result, or a statement that there was nothing to assess. */
    RESULT,
}

data class EmailCheckUiState(
    val stage: EmailStage = EmailStage.INPUT,
    val subject: String = "",
    val body: String = "",
    /**
     * The sender address, editable.
     *
     * Prefilled from a forwarded `From:` header when the mail app supplied one. It is left
     * editable because the share intent carries no sender field, so on many mail apps the
     * only way to check the sender is for the user to supply it.
     */
    val senderAddress: String = "",
    val outcome: EmailCheckOutcome? = null,
) {
    val canAnalyse: Boolean get() = subject.isNotBlank() || body.isNotBlank()
}

/**
 * Drives one email check: shared message -> the existing text analysis pipeline.
 *
 * No analysis logic lives here, and no email-specific scoring exists anywhere. Every judgement
 * comes from [AnalyzeEmailUseCase], which delegates to the same `AnalyzeTextUseCase` that a
 * pasted SMS uses. Email is an *input surface*, not a detector.
 */
@HiltViewModel
class EmailCheckViewModel @Inject constructor(
    private val analyzeEmail: AnalyzeEmailUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailCheckUiState())
    val uiState: StateFlow<EmailCheckUiState> = _uiState.asStateFlow()

    /**
     * Called once with whatever a mail app shared.
     *
     * The sender is parsed here rather than at analysis time so the user can see and correct
     * it before anything is looked up — the address is the one part of an email whose host
     * leaves the device.
     */
    fun onShared(subject: String?, sharedText: String) {
        viewModelScope.launch {
            val parsed = analyzeEmail.parseOnly(subject, sharedText)
            _uiState.value = EmailCheckUiState(
                stage = EmailStage.INPUT,
                subject = parsed.subject.orEmpty(),
                body = parsed.body,
                senderAddress = parsed.senderAddress.orEmpty(),
            )
        }
    }

    fun onSubjectChanged(value: String) {
        _uiState.update { it.copy(subject = value, outcome = null, stage = EmailStage.INPUT) }
    }

    fun onBodyChanged(value: String) {
        _uiState.update { it.copy(body = value, outcome = null, stage = EmailStage.INPUT) }
    }

    fun onSenderChanged(value: String) {
        _uiState.update { it.copy(senderAddress = value, outcome = null, stage = EmailStage.INPUT) }
    }

    fun analyse() {
        val state = _uiState.value
        if (!state.canAnalyse || state.stage == EmailStage.WORKING) return

        _uiState.update { it.copy(stage = EmailStage.WORKING) }

        viewModelScope.launch {
            val outcome = runCatching {
                analyzeEmail(
                    subject = state.subject.takeIf { it.isNotBlank() },
                    sharedText = state.body,
                    senderOverride = state.senderAddress.takeIf { it.isNotBlank() },
                )
            }.getOrElse { EmailCheckOutcome.NothingToAnalyse }

            _uiState.update { it.copy(stage = EmailStage.RESULT, outcome = outcome) }
        }
    }

    fun reset() {
        _uiState.value = EmailCheckUiState()
    }
}
