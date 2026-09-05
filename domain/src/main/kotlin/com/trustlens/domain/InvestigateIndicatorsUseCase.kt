package com.trustlens.domain

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The outcome of consulting every configured reputation provider.
 *
 * [degraded] is true whenever the external picture is incomplete — no provider configured,
 * a lookup that failed, timed out or was rate-limited. It is reported rather than hidden,
 * because a user told "nothing was found" deserves to know whether anyone actually looked.
 */
data class ThreatIntelReport(
    val evidence: List<ExternalEvidence>,
    val degraded: Boolean,
) {
    companion object {
        /** Nothing was consulted at all. Degraded by definition. */
        val NotConsulted = ThreatIntelReport(evidence = emptyList(), degraded = true)
    }
}

/**
 * Consults reputation providers about the indicators found in a piece of content.
 *
 * Pure orchestration — it owns no protocol, no HTTP and no provider knowledge. Providers are
 * [ReputationSource] implementations from `:threatintel`, so the network stays behind an
 * interface and this class stays testable with fakes on the JVM.
 *
 * Four behaviours it is responsible for:
 *
 *  - **Ask about as little as possible.** Subjects are deduplicated before any request, so
 *    a message repeating the same link five times produces one lookup per provider.
 *  - **A provider cannot take the analysis down with it.** Each lookup is individually
 *    bounded and individually caught. One throwing or hanging provider yields
 *    [ReputationVerdict.UNKNOWN] for that lookup and nothing more.
 *  - **Absence is recorded, not discarded.** A timeout produces evidence of *not knowing*,
 *    which is a different thing from evidence of safety and is scored as neither.
 *  - **Order is deterministic.** Results are sorted before returning, so identical input
 *    always produces an identical [InvestigationContext] and therefore an identical score.
 */
class InvestigateIndicatorsUseCase @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards ReputationSource>,
) {

    /**
     * @param observedUrls the concrete URLs these subjects were extracted from, passed to
     *   providers purely as local context. Nothing here is transmitted — see
     *   [ReputationSource.lookup].
     */
    suspend fun investigate(
        subjects: Collection<String>,
        observedUrls: Collection<String> = emptyList(),
    ): ThreatIntelReport {
        val distinct = subjects
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (sources.isEmpty()) return ThreatIntelReport.NotConsulted
        if (distinct.isEmpty()) {
            // Nothing worth asking about is not the same as a provider outage: there was
            // simply no lookupable indicator in this content.
            return ThreatIntelReport(evidence = emptyList(), degraded = false)
        }

        val urls = observedUrls.filter { it.isNotBlank() }.distinct()
        val gate = Semaphore(MAX_CONCURRENT_LOOKUPS)

        val evidence = coroutineScope {
            distinct.flatMap { subject ->
                sources.map { source -> source to subject }
            }.map { (source, subject) ->
                async { gate.withPermit { lookupSafely(source, subject, urls) } }
            }.awaitAll()
        }

        return ThreatIntelReport(
            evidence = evidence.sortedWith(
                compareBy({ it.sourceName }, { it.subject }),
            ),
            degraded = evidence.any { it.verdict in INCOMPLETE_VERDICTS },
        )
    }

    /**
     * Runs one lookup with a hard time limit and total failure isolation.
     *
     * A provider that throws, hangs, or returns nonsense must not be able to fail the
     * investigation — and must not be able to look like a clean result either, which is why
     * every failure path lands on [ReputationVerdict.UNKNOWN] or
     * [ReputationVerdict.ERROR] rather than on silence.
     */
    private suspend fun lookupSafely(
        source: ReputationSource,
        subject: String,
        observedUrls: List<String>,
    ): ExternalEvidence {
        val result = runCatching {
            withTimeoutOrNull(LOOKUP_TIMEOUT_MILLIS) { source.lookup(subject, observedUrls) }
        }

        return result.getOrNull()
            ?: ExternalEvidence(
                sourceName = source.sourceName,
                verdict = if (result.isFailure) {
                    ReputationVerdict.ERROR
                } else {
                    ReputationVerdict.UNKNOWN
                },
                subject = subject,
                summary = if (result.isFailure) {
                    "${source.sourceName} could not be reached — treated as unknown, not safe"
                } else {
                    "${source.sourceName} did not answer in time — treated as unknown, not safe"
                },
            )
    }

    private companion object {
        /**
         * Bounded so a message full of links cannot open a burst of sockets, and so free
         * provider tiers are not tripped into rate limiting by our own parallelism.
         */
        const val MAX_CONCURRENT_LOOKUPS = 4

        /**
         * A user waiting on a verdict will not wait long. Past this the honest answer is
         * "we do not know yet", delivered promptly, rather than a better answer delivered
         * too late to matter.
         */
        const val LOOKUP_TIMEOUT_MILLIS = 6_000L

        /** Verdicts that mean the external picture is incomplete. */
        val INCOMPLETE_VERDICTS = setOf(
            ReputationVerdict.UNKNOWN,
            ReputationVerdict.ERROR,
            ReputationVerdict.CONFLICTING,
        )
    }
}
