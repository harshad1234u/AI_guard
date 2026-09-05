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

    override suspend fun lookup(subject: String): ExternalEvidence =
        lookup(subject, emptyList())

    override suspend fun lookup(subject: String, observedUrls: List<String>): ExternalEvidence {
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
            200 -> parse(host, response.body, observedUrls)

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

    private fun parse(
        host: String,
        body: String,
        observedUrls: List<String>,
    ): ExternalEvidence {
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
            "ok" -> describeListing(host, root, observedUrls)

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
     * Grades an `ok` listing into the strongest verdict the evidence actually supports.
     *
     * ## The two questions URLhaus can answer, and the difference between them
     *
     * URLhaus tracks *URLs*, not hosts, and never forgets one. Any host that has ever carried
     * a reported payload keeps its entries permanently, so every large shared platform is
     * permanently listed — `github.com` currently reports 7,939 URLs, 19 of them live, all of
     * them somebody else's `/releases/download/...` path. Answering "is this host listed?"
     * therefore says almost nothing about the link a user is actually holding.
     *
     * Fortunately the host response enumerates the offending URLs themselves, so the sharper
     * question can be answered **on this device**: is the link in front of the user one of
     * them? [observedUrls] is compared locally and is never transmitted — the request is still
     * `host=` and nothing else.
     *
     * That yields four cases, in descending order of what the evidence supports:
     *
     *  1. **This exact URL is listed and live** -> CONFIRMED. The strongest claim available.
     *  2. **This exact URL is listed but offline** -> SUSPICIOUS (Phase 14A semantics, now at
     *     URL precision).
     *  3. **No URL context at all** — the user asked about a bare host — -> the host-level
     *     grading from Phase 14A, because the host *is* the question being asked.
     *  4. **The host is listed, this URL is not among the entries** -> SUSPICIOUS, worded as a
     *     statement about the host. This is the shared-hosting case.
     *
     * ## Why case 4 is never NO_DETECTION
     *
     * The API returns at most 100 entries of a possibly far larger set (`url_count` is 7,939
     * for `github.com`). Not finding the link among the returned entries is therefore *not*
     * evidence that it is absent from the full list, let alone evidence that it is safe. It
     * stays SUSPICIOUS and keeps contributing risk. UNKNOWN is not SAFE, and neither is
     * "not in the first hundred".
     *
     * No weight, band or threshold is touched here; only the strength of the claim changes.
     */
    private fun describeListing(
        host: String,
        root: kotlinx.serialization.json.JsonObject,
        observedUrls: List<String>,
    ): ExternalEvidence {
        val urls = runCatching { root["urls"]!!.jsonArray }.getOrNull()
        val total = urls?.size ?: 0

        if (total == 0) {
            return unknown(
                host,
                "URLhaus reported a listing for $host with no entries — " +
                    "treated as unknown, not safe",
            )
        }

        val entries = urls.orEmpty().map { element ->
            val obj = runCatching { element.jsonObject }.getOrNull()
            val url = obj?.let {
                runCatching { it["url"]!!.jsonPrimitive.contentOrNull }.getOrNull()
            }
            val isOnline = obj?.let {
                runCatching { it["url_status"]!!.jsonPrimitive.contentOrNull == "online" }
                    .getOrDefault(false)
            } ?: false
            url to isOnline
        }

        val online = entries.count { it.second }

        // How many reports URLhaus holds in total, which may exceed what it returned.
        val reportedTotal = runCatching {
            root["url_count"]!!.jsonPrimitive.contentOrNull?.trim()?.toInt()
        }.getOrNull()
        val truncated = reportedTotal != null && reportedTotal > total

        val wanted = observedUrls.mapNotNull(::matchKey).toSet()
        val matched = if (wanted.isEmpty()) {
            emptyList()
        } else {
            entries.filter { (url, _) -> url != null && matchKey(url) in wanted }
        }
        val matchedOnline = matched.count { it.second }

        val counts = buildMap {
            put("listed_urls", total.toString())
            put("online_urls", online.toString())
            if (reportedTotal != null) put("host_total_urls", reportedTotal.toString())
        }

        fun evidence(
            verdict: ReputationVerdict,
            scope: String,
            listing: String,
            summary: String,
        ) = ExternalEvidence(
            sourceName = sourceName,
            verdict = verdict,
            subject = host,
            summary = summary,
            details = counts + mapOf("scope" to scope, "listing" to listing),
        )

        return when {
            // 1. The link itself is listed and is being served right now.
            matchedOnline > 0 -> evidence(
                ReputationVerdict.CONFIRMED,
                scope = "url",
                listing = "active",
                summary = "URLhaus reports this exact URL as malicious and currently online",
            )

            // 2. The link itself is listed, but is not currently serving.
            matched.isNotEmpty() -> evidence(
                ReputationVerdict.SUSPICIOUS,
                scope = "url",
                listing = "historical",
                summary = "URLhaus reports this exact URL as malicious, though it is not " +
                    "currently online — a past-abuse record, not a clean one",
            )

            // 3. No URL to check: the host itself was the question. Phase 14A grading.
            wanted.isEmpty() && online > 0 -> evidence(
                ReputationVerdict.CONFIRMED,
                scope = "host",
                listing = "active",
                summary = "URLhaus lists $total malicious URL(s) on $host, " +
                    "$online currently online",
            )

            wanted.isEmpty() -> evidence(
                ReputationVerdict.SUSPICIOUS,
                scope = "host",
                listing = "historical",
                summary = "URLhaus lists $total malicious URL(s) previously hosted on $host, " +
                    "none currently online — a past-abuse record, not a clean one",
            )

            // 4. The host is listed; this particular link is not among the entries returned.
            //    Report what is true — about the host — and say so in those words.
            else -> evidence(
                ReputationVerdict.SUSPICIOUS,
                scope = "host",
                listing = if (online > 0) "active" else "historical",
                summary = buildString {
                    append("URLhaus reports malicious URLs hosted on $host")
                    if (online > 0) append(", $online currently online")
                    append(", but this link is not among them — ")
                    append("this is a report about the host, not about this URL")
                    if (truncated) {
                        append(" (only $total of $reportedTotal reports were returned, ")
                        append("so this is not proof the link is unlisted)")
                    }
                },
            )
        }
    }

    /**
     * A comparison key for one URL: authority plus path and query, with the scheme, any
     * credentials, the fragment and a trailing slash discarded.
     *
     * The scheme is deliberately ignored so a report of `http://host/payload` matches a user
     * checking `https://host/payload` — the same resource, and erring towards a match errs
     * towards the stronger verdict. Path and query keep their case, because they are
     * case-sensitive and two paths differing only in case are two different resources.
     *
     * String parsing rather than `java.net.URI`, which throws on exactly the malformed input
     * this adapter has to survive.
     */
    private fun matchKey(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val afterScheme = trimmed.substringAfter("://", missingDelimiterValue = trimmed)
        val withoutFragment = afterScheme.substringBefore('#')
        if (withoutFragment.isEmpty()) return null

        val slash = withoutFragment.indexOf('/')
        val authority = if (slash < 0) withoutFragment else withoutFragment.substring(0, slash)
        val rest = if (slash < 0) "" else withoutFragment.substring(slash)

        // Credentials before an `@` identify a user, not the resource.
        val hostAndPort = authority.substringAfterLast('@')
        if (hostAndPort.isEmpty()) return null

        return hostAndPort.lowercase() + rest.trimEnd('/')
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
