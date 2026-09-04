package com.trustlens.threatintel

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.ReputationVerdict
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in verification against the **real** VirusTotal and URLhaus APIs.
 *
 * Skipped unless run deliberately:
 *
 * ```
 * ./gradlew :threatintel:test -Dtrustlens.live=true
 * ```
 *
 * It is opt-in for the reasons ordinary unit tests must never touch a live API: it needs
 * credentials, consumes a rate-limit budget, and fails when someone else's service is down.
 * Every behavioural guarantee is covered by [VirusTotalSourceTest] and [UrlhausSourceTest]
 * against a fake transport; this exists solely to prove the wiring works against the real
 * thing, which no fake can establish.
 *
 * `example.com` is used throughout — IANA-reserved, benign, and safe to ask about. Nothing
 * here submits a URL for scanning, uploads a sample, or visits anything.
 *
 * Credentials are never printed. The assertions below check the opposite: that no key can be
 * found anywhere in the evidence a lookup produces.
 */
class LiveProviderVerificationTest {

    private val credentials = BuildConfigCredentials()
    private val transport = OkHttpTransport()

    /** IANA-reserved and safe to query. */
    private val benignHost = "example.com"

    private fun liveRunRequested() =
        System.getProperty("trustlens.live")?.equals("true", ignoreCase = true) == true

    private fun requireLive(credential: String?) {
        assumeTrue("live run not requested", liveRunRequested())
        assumeTrue("credential not configured", !credential.isNullOrBlank())
    }

    /** Fails loudly if a credential ever reaches something a user or a log could see. */
    private fun assertNoCredentialLeak(vararg rendered: String) {
        val haystack = rendered.joinToString(" ")
        listOf(credentials.virusTotalApiKey, credentials.urlhausAuthKey)
            .filterNot { it.isNullOrBlank() }
            .forEach { secret -> assertThat(haystack).doesNotContain(secret) }
    }

    @Test
    fun `virustotal answers a live lookup and maps to evidence`() = runTest {
        requireLive(credentials.virusTotalApiKey)

        val evidence = VirusTotalSource(transport, credentials).lookup(benignHost)

        println(
            "LIVE VirusTotal: verdict=${evidence.verdict} subject=${evidence.subject} " +
                "details=${evidence.details} summary=${evidence.summary}",
        )

        // Authentication worked: an unauthenticated or rejected call cannot produce a
        // verdict that depends on parsed engine counts.
        assertThat(evidence.verdict).isIn(
            listOf(
                ReputationVerdict.NO_DETECTION,
                ReputationVerdict.SUSPICIOUS,
                ReputationVerdict.CONFIRMED,
            ),
        )
        assertThat(evidence.sourceName).isEqualTo("VirusTotal")
        assertThat(evidence.subject).isEqualTo(benignHost)

        // Parsing produced real engine counts, not defaults.
        assertThat(evidence.details.keys).containsExactly("malicious", "suspicious", "engines")
        assertThat(evidence.details["engines"]!!.toInt()).isGreaterThan(0)

        assertNoCredentialLeak(evidence.summary, evidence.subject, evidence.details.toString())
    }

    @Test
    fun `urlhaus answers a live lookup and maps to evidence`() = runTest {
        requireLive(credentials.urlhausAuthKey)

        val evidence = UrlhausSource(transport, credentials).lookup(benignHost)

        println(
            "LIVE URLhaus: verdict=${evidence.verdict} subject=${evidence.subject} " +
                "details=${evidence.details} summary=${evidence.summary}",
        )

        // A rejected Auth-Key would surface as UNKNOWN; a parsed answer cannot.
        assertThat(evidence.verdict).isIn(
            listOf(ReputationVerdict.NO_DETECTION, ReputationVerdict.CONFIRMED),
        )
        assertThat(evidence.sourceName).isEqualTo("URLhaus")
        assertThat(evidence.subject).isEqualTo(benignHost)

        assertNoCredentialLeak(evidence.summary, evidence.subject, evidence.details.toString())
    }

    @Test
    fun `a deliberately invalid credential yields unknown against the real api`() = runTest {
        assumeTrue("live run not requested", liveRunRequested())

        val rejected = object : ProviderCredentials {
            override val virusTotalApiKey = "0000000000000000000000000000000000000000000000000000000000000000"
            override val urlhausAuthKey = "0000000000000000000000000000000000000000"
        }

        val virusTotal = VirusTotalSource(transport, rejected).lookup(benignHost)
        val urlhaus = UrlhausSource(transport, rejected).lookup(benignHost)

        println("LIVE rejected-credential: VirusTotal=${virusTotal.verdict} URLhaus=${urlhaus.verdict}")

        // The rule that matters: a rejected credential is never a clean result.
        listOf(virusTotal, urlhaus).forEach {
            assertThat(it.verdict).isIn(
                listOf(ReputationVerdict.UNKNOWN, ReputationVerdict.ERROR),
            )
            assertThat(it.summary).contains("not safe")
        }
    }

    @Test
    fun `an unreachable host yields error rather than a clean result`() = runTest {
        assumeTrue("live run not requested", liveRunRequested())

        // A real socket failure against a reserved-for-documentation address.
        val unreachable = object : HttpTransport {
            override suspend fun get(url: String, headers: Map<String, String>) =
                OkHttpTransport().get("https://192.0.2.1/never", headers)

            override suspend fun postForm(
                url: String,
                form: Map<String, String>,
                headers: Map<String, String>,
            ) = OkHttpTransport().postForm("https://192.0.2.1/never", form, headers)
        }

        val evidence = VirusTotalSource(unreachable, credentials).lookup(benignHost)

        println("LIVE unreachable: verdict=${evidence.verdict}")
        assertThat(evidence.verdict).isEqualTo(ReputationVerdict.ERROR)
        assertThat(evidence.summary).contains("not safe")
    }
}
