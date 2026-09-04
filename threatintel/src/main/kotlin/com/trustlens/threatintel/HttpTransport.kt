package com.trustlens.threatintel

/**
 * One HTTP exchange, reduced to what a reputation lookup actually needs.
 *
 * [body] is untrusted text from a third party. It is never executed, never rendered as
 * markup and never used to select an action — it is parsed into fixed fields and discarded.
 */
data class HttpResponse(
    val code: Int,
    val body: String,
)

/**
 * The seam between provider logic and the network.
 *
 * Provider adapters own parsing, verdict mapping and failure semantics — the interesting,
 * security-relevant half — and this interface lets all of that be tested on the JVM against
 * a fake, without a socket, a live API key, or a provider's rate limit.
 *
 * Implementations must not throw for HTTP error status codes; a 401 or a 429 is an answer,
 * and the adapter decides what it means.
 */
interface HttpTransport {

    /** @throws java.io.IOException when the request could not be completed at all. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse

    /** @throws java.io.IOException when the request could not be completed at all. */
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse
}

/**
 * Credentials for the reputation providers.
 *
 * Supplied at the composition root from a gitignored properties file. A missing credential
 * is a normal, expected state — it must produce [com.trustlens.domain.ReputationVerdict.UNKNOWN],
 * never a fabricated clean result.
 */
interface ProviderCredentials {
    val virusTotalApiKey: String?
    val urlhausAuthKey: String?
}
