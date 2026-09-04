package com.trustlens.threatintel

import java.io.IOException

/**
 * A scripted [HttpTransport] so provider adapters can be tested without a socket, a live
 * API key, or somebody's rate limit. Records what was sent, which is how the privacy claims
 * in these tests are actually checked rather than asserted.
 */
class FakeTransport(
    private val respond: (String) -> HttpResponse = { HttpResponse(200, "{}") },
) : HttpTransport {

    var requestedUrl: String? = null
    var sentHeaders: Map<String, String> = emptyMap()
    var sentForm: Map<String, String> = emptyMap()
    var callCount = 0

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        callCount++
        requestedUrl = url
        sentHeaders = headers
        return respond(url)
    }

    override suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): HttpResponse {
        callCount++
        requestedUrl = url
        sentForm = form
        sentHeaders = headers
        return respond(url)
    }

    companion object {
        fun replying(code: Int, body: String) = FakeTransport { HttpResponse(code, body) }

        /** The network is simply not there. */
        fun failing(message: String = "no route to host") = object : HttpTransport {
            override suspend fun get(url: String, headers: Map<String, String>) =
                throw IOException(message)

            override suspend fun postForm(
                url: String,
                form: Map<String, String>,
                headers: Map<String, String>,
            ): HttpResponse = throw IOException(message)
        }
    }
}

/** Credentials that are present, or deliberately absent. */
class FakeCredentials(
    override val virusTotalApiKey: String? = "vt-test-key",
    override val urlhausAuthKey: String? = "uh-test-key",
) : ProviderCredentials
