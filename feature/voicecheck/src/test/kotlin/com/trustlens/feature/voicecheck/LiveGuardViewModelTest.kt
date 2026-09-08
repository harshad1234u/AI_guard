package com.trustlens.feature.voicecheck

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.AiClassifier
import com.trustlens.domain.AnalyzeTextUseCase
import com.trustlens.domain.AnalyzeTranscriptUseCase
import com.trustlens.domain.ClassificationResult
import com.trustlens.domain.Confidence
import com.trustlens.domain.InferenceBackend
import com.trustlens.domain.Indicators
import com.trustlens.domain.IndicatorExtractor
import com.trustlens.domain.InvestigationContext
import com.trustlens.domain.Observation
import com.trustlens.domain.RiskAssessor
import com.trustlens.domain.RiskLevel
import com.trustlens.domain.RiskResult
import com.trustlens.domain.SafeAction
import com.trustlens.domain.TranscriptionBackend
import com.trustlens.domain.UrlAnalysis
import com.trustlens.domain.UrlAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for Live Guard's session behaviour.
 *
 * Live Guard adds a listening *mode*, not a detector. What is worth pinning here is the
 * behaviour it genuinely owns: what gets analysed, what does not, and what happens to the
 * verdict when a conversation moves on after a dangerous sentence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveGuardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * Returns a scripted score per analysis, so a test can drive the verdict up and back down.
     *
     * Deliberately not keyword-based. The transcript only ever grows, so a dangerous phrase
     * stays in it forever — the reason a score can still *fall* is dilution of the ML signal
     * across a longer transcript, which no keyword rule would model honestly. The scores are
     * simply stated, and what is under test is the view model's memory of the peak.
     */
    private class ScriptedAssessor(private val scores: List<Int> = listOf(0)) : RiskAssessor {
        var analysedTexts = mutableListOf<String>()

        override fun assess(context: InvestigationContext): RiskResult {
            val score = scores.getOrElse(analysedTexts.size) { scores.last() }
            analysedTexts += context.rawContent
            return RiskResult(
                score = score,
                level = RiskLevel.forScore(score),
                confidence = Confidence.MEDIUM,
                reasons = emptyList(),
                observations = emptyList(),
                externalEvidence = emptyList(),
                inferences = emptyList(),
                recommendedActions = listOf(SafeAction.PROCEED_CAREFULLY),
                degraded = true,
            )
        }
    }

    private class NoopExtractor : IndicatorExtractor {
        override fun extract(text: String) = Indicators()
    }

    private class NoopUrlAnalyzer : UrlAnalyzer {
        override fun analyse(url: String) = UrlAnalysis(
            original = url,
            normalised = url,
            scheme = null,
            host = null,
            registrableDomain = null,
            port = null,
            path = "/",
            query = null,
            fragment = null,
            subdomainLabels = emptyList(),
            findings = emptySet(),
            isParseable = false,
        )

        override fun observe(
            urls: List<String>,
            claimedOrganisations: List<String>,
        ): List<Observation> = emptyList()
    }

    private class NoopClassifier : AiClassifier {
        override val backend = InferenceBackend.MOCK
        override suspend fun classify(text: String) = ClassificationResult.Unavailable("mock")
    }

    private lateinit var assessor: ScriptedAssessor

    private fun viewModel(scores: List<Int> = listOf(0)): LiveGuardViewModel {
        assessor = ScriptedAssessor(scores)
        return LiveGuardViewModel(
            AnalyzeTranscriptUseCase(
                AnalyzeTextUseCase(
                    extractor = NoopExtractor(),
                    urlAnalyzer = NoopUrlAnalyzer(),
                    classifier = NoopClassifier(),
                    riskAssessor = assessor,
                ),
            ),
        )
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `segments accumulate into one rolling transcript`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onSegment("this is your bank")
        vm.onSegment("share the OTP now")
        advanceUntilIdle()

        assertThat(vm.uiState.value.transcript)
            .isEqualTo("this is your bank share the OTP now")
    }

    @Test
    fun `interim words are shown but never analysed`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onPartial("share the OT")
        advanceUntilIdle()

        assertThat(vm.uiState.value.partial).isEqualTo("share the OT")
        // A half-heard sentence must not produce a verdict about words nobody said.
        assertThat(assessor.analysedTexts).isEmpty()
    }

    @Test
    fun `rapid segments are debounced into a single analysis`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onSegment("one")
        vm.onSegment("two")
        vm.onSegment("three")
        advanceUntilIdle()

        // Three phrases in quick succession cost one analysis, not three.
        assertThat(assessor.analysedTexts).containsExactly("one two three")
    }

    @Test
    fun `the peak is remembered after the conversation moves on`() = runTest(dispatcher) {
        // First analysis scores HIGH; the second, over a longer transcript, scores LOW.
        val vm = viewModel(scores = listOf(70, 5))
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onSegment("please share the OTP")
        advanceUntilIdle()
        assertThat(vm.uiState.value.peakScore).isEqualTo(70)

        // The scammer changes the subject and the live figure falls...
        vm.onSegment("anyway the weather is nice")
        advanceUntilIdle()
        assertThat(vm.uiState.value.result?.risk?.score).isEqualTo(5)

        // ...but the worst moment of the call is still what the user is shown.
        assertThat(vm.uiState.value.peakScore).isEqualTo(70)
        assertThat(vm.uiState.value.peakLevel).isEqualTo(RiskLevel.HIGH)
        assertThat(vm.uiState.value.alerting).isTrue()
    }

    @Test
    fun `an unchanged transcript is never analysed twice`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onSegment("share the OTP")
        advanceUntilIdle()
        vm.onListeningEnded()
        advanceUntilIdle()

        assertThat(assessor.analysedTexts).hasSize(1)
    }

    @Test
    fun `words spoken just before stopping are still analysed`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onSegment("share the OTP")
        // Stopped before the debounce could fire.
        vm.onListeningEnded()
        advanceUntilIdle()

        assertThat(assessor.analysedTexts).containsExactly("share the OTP")
        assertThat(vm.uiState.value.stage).isEqualTo(LiveStage.ENDED)
    }

    @Test
    fun `silence produces no verdict at all`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onListeningEnded()
        advanceUntilIdle()

        // Hearing nothing is not the same as finding nothing wrong.
        assertThat(assessor.analysedTexts).isEmpty()
        assertThat(vm.uiState.value.result).isNull()
        assertThat(vm.uiState.value.alerting).isFalse()
    }

    @Test
    fun `losing the microphone is surfaced, not silently ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

        vm.onHealth(ListeningHealth.NO_AUDIO)
        assertThat(vm.uiState.value.health).isEqualTo(ListeningHealth.NO_AUDIO)

        vm.onListeningEnded("The microphone could not be read.")
        advanceUntilIdle()

        assertThat(vm.uiState.value.problem).isEqualTo("The microphone could not be read.")
    }

    @Test
    fun `a detected language is shown but changes nothing about the verdict`() =
        runTest(dispatcher) {
            val vm = viewModel(scores = listOf(70))
            vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)

            vm.onLanguageDetected("hi-IN")
            vm.onSegment("OTP बताइए")
            advanceUntilIdle()

            assertThat(vm.uiState.value.detectedLanguage).isEqualTo("hi-IN")
            // The transcript reaches the pipeline verbatim; nothing branches on language.
            assertThat(assessor.analysedTexts).containsExactly("OTP बताइए")
            assertThat(vm.uiState.value.result?.risk?.score).isEqualTo(70)
        }

    @Test
    fun `starting over clears the previous session entirely`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onListeningStarted(TranscriptionBackend.ON_DEVICE)
        vm.onSegment("share the OTP")
        advanceUntilIdle()

        vm.reset()

        assertThat(vm.uiState.value.transcript).isEmpty()
        assertThat(vm.uiState.value.result).isNull()
        assertThat(vm.uiState.value.peakScore).isEqualTo(0)
        assertThat(vm.uiState.value.stage).isEqualTo(LiveStage.IDLE)
    }
}
