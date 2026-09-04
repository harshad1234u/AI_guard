package com.trustlens.threatintel

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The only place in TrustLens that opens a network connection.
 *
 * Everything about it is bounded on purpose. Timeouts are short because a user waiting on a
 * verdict will not wait long, and retries are disabled outright: a provider that is rate
 * limiting us is not a provider to hammer, and a retry loop behind a UI is how an app
 * becomes unresponsive.
 *
 * Note what this class never does. It does not follow a link found in content, it does not
 * fetch a page, and it does not resolve or expand a URL. It talks to two hard-coded
 * reputation APIs about a domain name. Investigating a link must never mean visiting it.
 */
@Singleton
class OkHttpTransport @Inject constructor() : HttpTransport {

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // One attempt. The caller decides what a failure means; it does not mean "try
        // harder", and an automatic retry would double our load on a rate-limited API.
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
        execute(
            Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .get()
                .build(),
        )

    override suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): HttpResponse = execute(
        Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(
                FormBody.Builder()
                    .apply { form.forEach { (name, value) -> add(name, value) } }
                    .build(),
            )
            .build(),
    )

    private suspend fun execute(request: Request): HttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)

            continuation.invokeOnCancellation { runCatching { call.cancel() } }

            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        // Surfaced as IOException so adapters map it to a not-safe verdict
                        // rather than mistaking it for an answer.
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val body = runCatching { it.body?.string().orEmpty() }
                                .getOrDefault("")
                            continuation.resume(HttpResponse(it.code, body))
                        }
                    }
                },
            )
        }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 4L
        const val READ_TIMEOUT_SECONDS = 4L
        const val CALL_TIMEOUT_SECONDS = 6L
    }
}
