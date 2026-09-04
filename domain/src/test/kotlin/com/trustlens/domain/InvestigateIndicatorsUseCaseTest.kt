package com.trustlens.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for threat-intelligence orchestration.
 *
 * The properties that matter are: ask as little as possible, let no provider take the
 * analysis down with it, and never let a missing answer look like a reassuring one.
 */
class InvestigateIndicatorsUseCaseTest {

    private class FakeSource(
        override val sourceName: String,
        private val verdict: ReputationVerdict = ReputationVerdict.NO_DETECTION,
        private val delayMillis: Long = 0,
        private val failWith: Throwable? = null,
    ) : ReputationSource {
        val queried = mutableListOf<String>()

        override suspend fun lookup(subject: String): ExternalEvidence {
            queried += subject
            if (delayMillis > 0) delay(delayMillis)
            failWith?.let { throw it }
            return ExternalEvidence(
                sourceName = sourceName,
                verdict = verdict,
                subject = subject,
                summary = "$sourceName says $verdict about $subject",
            )
        }
    }

    // ---- request minimisation ---------------------------------------------------------------

    @Test
    fun `repeated indicators are queried once`() = runTest {
        val source = FakeSource("A")

        val report = InvestigateIndicatorsUseCase(setOf(source)).investigate(
            listOf("example.com", "example.com", "EXAMPLE.com", "  example.com  "),
        )

        assertThat(source.queried).containsExactly("example.com")
        assertThat(report.evidence).hasSize(1)
    }

    @Test
    fun `blank indicators are dropped`() = runTest {
        val source = FakeSource("A")

        InvestigateIndicatorsUseCase(setOf(source)).investigate(listOf("", "   ", "\n"))

        assertThat(source.queried).isEmpty()
    }

    @Test
    fun `every source is asked about every distinct indicator`() = runTest {
        val a = FakeSource("A")
        val b = FakeSource("B")

        val report = InvestigateIndicatorsUseCase(setOf(a, b))
            .investigate(listOf("one.com", "two.com"))

        assertThat(a.queried).containsExactly("one.com", "two.com")
        assertThat(b.queried).containsExactly("one.com", "two.com")
        assertThat(report.evidence).hasSize(4)
    }

    // ---- degraded reporting ------------------------------------------------------------------

    @Test
    fun `no configured provider means not consulted and degraded`() = runTest {
        val report = InvestigateIndicatorsUseCase(emptySet()).investigate(listOf("example.com"))

        assertThat(report).isEqualTo(ThreatIntelReport.NotConsulted)
        assertThat(report.degraded).isTrue()
    }

    @Test
    fun `nothing worth looking up is not a provider outage`() = runTest {
        val report = InvestigateIndicatorsUseCase(setOf(FakeSource("A")))
            .investigate(emptyList())

        // There was simply no lookupable indicator — that is not a degraded external picture.
        assertThat(report.evidence).isEmpty()
        assertThat(report.degraded).isFalse()
    }

    @Test
    fun `usable answers from every provider are not degraded`() = runTest {
        val report = InvestigateIndicatorsUseCase(
            setOf(
                FakeSource("A", ReputationVerdict.CONFIRMED),
                FakeSource("B", ReputationVerdict.NO_DETECTION),
            ),
        ).investigate(listOf("example.com"))

        assertThat(report.degraded).isFalse()
    }

    @Test
    fun `one unusable provider marks the report degraded`() = runTest {
        val report = InvestigateIndicatorsUseCase(
            setOf(
                FakeSource("A", ReputationVerdict.CONFIRMED),
                FakeSource("B", ReputationVerdict.UNKNOWN),
            ),
        ).investigate(listOf("example.com"))

        assertThat(report.degraded).isTrue()
    }

    // ---- failure isolation --------------------------------------------------------------------

    @Test
    fun `a throwing provider becomes error evidence, not an exception`() = runTest {
        val report = InvestigateIndicatorsUseCase(
            setOf(FakeSource("A", failWith = IllegalStateException("boom"))),
        ).investigate(listOf("example.com"))

        val evidence = report.evidence.single()
        assertThat(evidence.verdict).isEqualTo(ReputationVerdict.ERROR)
        assertThat(evidence.summary).contains("not safe")
    }

    @Test
    fun `a hanging provider times out as unknown`() = runTest {
        val report = InvestigateIndicatorsUseCase(
            setOf(FakeSource("Slow", delayMillis = 60_000)),
        ).investigate(listOf("example.com"))

        val evidence = report.evidence.single()
        assertThat(evidence.verdict).isEqualTo(ReputationVerdict.UNKNOWN)
        assertThat(evidence.summary).contains("did not answer in time")
    }

    @Test
    fun `one provider failing does not cancel the other`() = runTest {
        val healthy = FakeSource("Healthy", ReputationVerdict.CONFIRMED)

        val report = InvestigateIndicatorsUseCase(
            setOf(FakeSource("Broken", failWith = RuntimeException("down")), healthy),
        ).investigate(listOf("example.com"))

        assertThat(healthy.queried).containsExactly("example.com")
        assertThat(report.evidence.map { it.verdict })
            .containsExactly(ReputationVerdict.CONFIRMED, ReputationVerdict.ERROR)
    }

    @Test
    fun `a failed provider is never converted to a clean result`() = runTest {
        val report = InvestigateIndicatorsUseCase(
            setOf(
                FakeSource("Broken", failWith = RuntimeException("down")),
                FakeSource("Slow", delayMillis = 60_000),
            ),
        ).investigate(listOf("example.com"))

        assertThat(report.evidence.map { it.verdict })
            .containsNoneOf(ReputationVerdict.NO_DETECTION, ReputationVerdict.CONFIRMED)
        assertThat(report.degraded).isTrue()
    }

    @Test
    fun `both providers failing still returns a usable report`() = runTest {
        val report = InvestigateIndicatorsUseCase(
            setOf(
                FakeSource("A", failWith = RuntimeException("x")),
                FakeSource("B", failWith = RuntimeException("y")),
            ),
        ).investigate(listOf("example.com"))

        assertThat(report.evidence).hasSize(2)
        assertThat(report.degraded).isTrue()
    }

    // ---- determinism ----------------------------------------------------------------------------

    @Test
    fun `evidence order is stable regardless of completion order`() = runTest {
        val report = InvestigateIndicatorsUseCase(
            setOf(
                FakeSource("Zebra", delayMillis = 5),
                FakeSource("Alpha", delayMillis = 50),
            ),
        ).investigate(listOf("second.com", "first.com"))

        assertThat(report.evidence.map { "${it.sourceName}/${it.subject}" })
            .containsExactly(
                "Alpha/first.com", "Alpha/second.com",
                "Zebra/first.com", "Zebra/second.com",
            )
            .inOrder()
    }

    @Test
    fun `repeated investigations produce identical reports`() = runTest {
        val useCase = InvestigateIndicatorsUseCase(
            setOf(FakeSource("A", ReputationVerdict.CONFIRMED)),
        )

        val reports = (1..10).map { useCase.investigate(listOf("example.com")) }

        assertThat(reports.distinct()).hasSize(1)
    }
}
