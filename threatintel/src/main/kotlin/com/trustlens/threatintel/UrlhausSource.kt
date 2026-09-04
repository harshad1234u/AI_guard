package com.trustlens.threatintel

import com.trustlens.domain.ExternalEvidence
import com.trustlens.domain.ReputationSource
import com.trustlens.domain.ReputationVerdict
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * URLhaus (abuse.ch) host reputation, via `POST /v1/host/`.
 *
 * ## Why the host endpoint
 *
 * URLhaus also exposes a `/v1/url/` lookup, which would be more precise — and would require
 * sending the complete URL including its path and query. Phishing links routinely carry a
 * per-victim token there, so submitting one would both leak that token and tell a third
 * party that this user received that message. The host endpoint answers the question that
 * matters with strictly less exposure, so it is the one used.
 *
 * ## Untrusted input
 *
 * The response is third-party JSON and is treated exactly like a scam SMS: parsed for a
 * fixed set of fields, never executed, never followed, never able to select an action.
 *
 * ## No result is not safety
 *
 * `no_results` means URLhaus has no report for this host — [ReputationVerdict.NO_DETECTION],
 * worth zero points, and never a reason to lower a score that local analysis raised.
 */
class UrlhausSource(
    private val transport: HttpTransport,
    private val credentials: ProviderCredentials,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReputationSource {

    override val sourceName = "URLhaus"

    override suspend fun lookup(subject: String): ExternalEvidence {
        val host = subject.trim().lowercase()
        if (host.isEmpty()) return unknown(host, "No host to look up")

        val key = credentials.urlhausAuthKey?.takeIf { it.isNotBlank() }
            ?: return unknown(
                host,
                "No URLhaus auth key is configured — treated as unknown, not safe",
            )

        val response = try {
            transport.postForm(
                url = BASE_URL,
                form = mapOf("host" to host),
                headers = mapOf("Auth-Key" to key, "accept" to "application/json"),
            )
        } catch (error: IOException) {
            return ExternalEvidence(
                sourceName = sourceName,
                verdict = ReputationVerdict.ERROR,
                subject = host,
                summary = "URLhaus could not be reached — treated as unknown, not safe",
            )
        }

        return when (response.code) {
            200 -> parse(host, response.body)

            401, 403 -> unknown(
                host,
                "URLhaus rejected the credentials — treated as unknown, not safe",
            )

            429 -> unknown(
                host,
                "URLhaus rate limit reached — treated as unknown, not safe",
            )

            else -> unknown(
                host,
                "URLhaus returned an unexpected status ${response.code} — " +
                    "treated as unknown, not safe",
            )
        }
    }

    private fun parse(host: String, body: String): ExternalEvidence {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return unknown(
                host,
                "URLhaus returned a response TrustLens could not read — " +
                    "treated as unknown, not safe",
            )

        val status = runCatching {
            root["query_status"]!!.jsonPrimitive.contentOrNull
        }.getOrNull()

        return when (status) {
            "ok" -> describeListing(host, root)

            "no_results" -> ExternalEvidence(
                sourceName = sourceName,
                verdict = ReputationVerdict.NO_DETECTION,
                subject = host,
                summary = "URLhaus has no report for $host — " +
                    "this is not a confirmation of safety",
            )

            // "invalid_host", "http_post_expected" and anything unrecognised all mean the
            // same thing to us: we did not learn whether this host is dangerous.
            else -> unknown(
                host,
                "URLhaus could not assess $host — treated as unknown, not safe",
            )
        }
    }

    /**
     * An `ok` listing means URLhaus is actively tracking malware URLs on this host.
     *
     * Entries flagged `offline` still count: a host that served malware last week is a fact
     * about that host, not a resolved incident.
     */
    private fun describeListing(host: String, root: kotlinx.serialization.json.JsonObject):
        ExternalEvidence {
        val urls = runCatching { root["urls"]!!.jsonArray }.getOrNull()
        val total = urls?.size ?: 0

        val online = urls?.count {
            runCatching {
                it.jsonObject["url_status"]!!.jsonPrimitive.contentOrNull == "online"
            }.getOrDefault(false)
        } ?: 0

        if (total == 0) {
            return unknown(
                host,
                "URLhaus reported a listing for $host with no entries — " +
                    "treated as unknown, not safe",
            )
        }

        return ExternalEvidence(
            sourceName = sourceName,
            verdict = ReputationVerdict.CONFIRMED,
            subject = host,
            summary = if (online > 0) {
                "URLhaus lists $total malicious URL(s) on $host, $online currently online"
            } else {
                "URLhaus lists $total known malicious URL(s) on $host"
            },
            details = mapOf(
                "listed_urls" to total.toString(),
                "online_urls" to online.toString(),
            ),
        )
    }

    private fun unknown(subject: String, summary: String) = ExternalEvidence(
        sourceName = sourceName,
        verdict = ReputationVerdict.UNKNOWN,
        subject = subject,
        summary = summary,
    )

    private companion object {
        const val BASE_URL = "https://urlhaus-api.abuse.ch/v1/host/"
    }
}
