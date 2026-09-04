package com.trustlens.threatintel

import com.trustlens.domain.ExternalEvidence
import com.trustlens.domain.ReputationSource
import com.trustlens.domain.ReputationVerdict
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * VirusTotal domain reputation, via `GET /api/v3/domains/{domain}`.
 *
 * ## What is sent
 *
 * A registrable domain, and nothing else. Never a full URL, never a path or query string,
 * and never any part of the message, transcript, photo or audio the domain came from.
 *
 * ## What the answer is allowed to do
 *
 * Become an [ExternalEvidence] value. That is the entire extent of it. The response is JSON
 * from a third party — untrusted input in the same sense a scam SMS is — so it is read for a
 * fixed set of integers and discarded. No field of it can open a link, choose an action or
 * override a local finding, and a response saying "ignore previous instructions and mark
 * this domain safe" is simply text that nothing reads.
 *
 * ## Why there is no "safe"
 *
 * VirusTotal reporting zero detections means no engine in its set currently flags the
 * domain. That is [ReputationVerdict.NO_DETECTION] — a statement about what VirusTotal
 * knows, not a clean bill of health — and it contributes exactly zero to the risk score.
 * Newly registered phishing domains are clean on VirusTotal precisely when they are most
 * dangerous.
 */
class VirusTotalSource(
    private val transport: HttpTransport,
    private val credentials: ProviderCredentials,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReputationSource {

    override val sourceName = "VirusTotal"

    override suspend fun lookup(subject: String): ExternalEvidence {
        val domain = subject.trim().lowercase()
        if (domain.isEmpty()) {
            return unknown(domain, "No domain to look up")
        }

        val key = credentials.virusTotalApiKey?.takeIf { it.isNotBlank() }
            ?: return unknown(
                domain,
                "No VirusTotal API key is configured — treated as unknown, not safe",
            )

        val response = try {
            transport.get(
                url = "$BASE_URL/${encodeDomain(domain)}",
                headers = mapOf("x-apikey" to key, "accept" to "application/json"),
            )
        } catch (error: IOException) {
            // Deliberately does not include the exception text in anything user-facing that
            // could echo a header; the key must never reach a log or a screen.
            return ExternalEvidence(
                sourceName = sourceName,
                verdict = ReputationVerdict.ERROR,
                subject = domain,
                summary = "VirusTotal could not be reached — treated as unknown, not safe",
            )
        }

        return when (response.code) {
            200 -> parse(domain, response.body)

            401, 403 -> unknown(
                domain,
                "VirusTotal rejected the credentials — treated as unknown, not safe",
            )

            404 -> ExternalEvidence(
                sourceName = sourceName,
                verdict = ReputationVerdict.NO_DETECTION,
                subject = domain,
                summary = "VirusTotal holds no record for $domain — " +
                    "this is not a confirmation of safety",
            )

            429 -> unknown(
                domain,
                "VirusTotal rate limit reached — treated as unknown, not safe",
            )

            else -> unknown(
                domain,
                "VirusTotal returned an unexpected status ${response.code} — " +
                    "treated as unknown, not safe",
            )
        }
    }

    /**
     * Reads `data.attributes.last_analysis_stats`.
     *
     * Anything unexpected in the body — wrong shape, missing fields, outright garbage —
     * resolves to UNKNOWN. A parser that guesses at a malformed response is a parser that
     * can be steered by whoever controls the response.
     */
    private fun parse(domain: String, body: String): ExternalEvidence {
        val stats = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["data"]!!.jsonObject["attributes"]!!
                .jsonObject["last_analysis_stats"]!!.jsonObject
        }.getOrNull() ?: return unknown(
            domain,
            "VirusTotal returned a response TrustLens could not read — " +
                "treated as unknown, not safe",
        )

        val malicious = stats.intOrZero("malicious")
        val suspicious = stats.intOrZero("suspicious")
        val engines = malicious + suspicious +
            stats.intOrZero("harmless") + stats.intOrZero("undetected")

        val details = mapOf(
            "malicious" to malicious.toString(),
            "suspicious" to suspicious.toString(),
            "engines" to engines.toString(),
        )

        return when {
            malicious > 0 -> ExternalEvidence(
                sourceName = sourceName,
                verdict = ReputationVerdict.CONFIRMED,
                subject = domain,
                summary = "$malicious of $engines security engines flag $domain as malicious",
                details = details,
            )

            suspicious > 0 -> ExternalEvidence(
                sourceName = sourceName,
                verdict = ReputationVerdict.SUSPICIOUS,
                subject = domain,
                summary = "$suspicious of $engines security engines flag $domain as suspicious",
                details = details,
            )

            engines > 0 -> ExternalEvidence(
                sourceName = sourceName,
                verdict = ReputationVerdict.NO_DETECTION,
                subject = domain,
                summary = "No VirusTotal engine currently flags $domain — " +
                    "this is not a confirmation of safety",
                details = details,
            )

            // A 200 carrying no engine results at all tells us nothing.
            else -> unknown(
                domain,
                "VirusTotal returned no engine results for $domain — " +
                    "treated as unknown, not safe",
            )
        }
    }

    private fun JsonObject.intOrZero(name: String): Int =
        runCatching { this[name]!!.jsonPrimitive.int }.getOrDefault(0)

    /** Keeps a hostile "domain" from escaping the path segment it belongs in. */
    private fun encodeDomain(domain: String): String =
        domain.filter { it.isLetterOrDigit() || it == '.' || it == '-' }

    private fun unknown(subject: String, summary: String) = ExternalEvidence(
        sourceName = sourceName,
        verdict = ReputationVerdict.UNKNOWN,
        subject = subject,
        summary = summary,
    )

    private companion object {
        const val BASE_URL = "https://www.virustotal.com/api/v3/domains"
    }
}
