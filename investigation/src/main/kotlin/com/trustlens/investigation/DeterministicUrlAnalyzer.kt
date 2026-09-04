package com.trustlens.investigation

import com.trustlens.domain.Observation
import com.trustlens.domain.ObservationKind
import com.trustlens.domain.UrlAnalysis
import com.trustlens.domain.UrlAnalyzer
import com.trustlens.domain.UrlFinding
import javax.inject.Inject

/**
 * Offline URL analysis.
 *
 * Everything here is string manipulation. There is no `java.net`, no HTTP client, no DNS
 * lookup, no redirect following and no shortener expansion — a shortened link stays a
 * shortened link, and an unresolvable host analyses exactly as well as a real one. That is
 * deliberate: the product's promise is that TrustLens inspects a link *without* visiting it,
 * so the user is never the first person to touch a hostile URL.
 *
 * URLs are parsed by hand rather than with [java.net.URI] because URI throws on the malformed
 * input this analyser most needs to survive.
 */
class DeterministicUrlAnalyzer @Inject constructor() : UrlAnalyzer {

    override fun analyse(url: String): UrlAnalysis {
        val trimmed = url.trim().trimEnd(*TRAILING_PUNCTUATION)
        if (trimmed.isEmpty()) return unparseable(url)

        val scheme = SCHEME.find(trimmed)?.groupValues?.get(1)?.lowercase()
        val afterScheme = if (scheme != null) trimmed.substringAfter("://") else trimmed

        val authorityAndRest = afterScheme
        val authority = authorityAndRest
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (authority.isEmpty()) return unparseable(url)

        val hasCredentials = '@' in authority
        val hostAndPort = authority.substringAfterLast('@')

        val (host, port) = splitHostAndPort(hostAndPort) ?: return unparseable(url)
        if (host.isEmpty()) return unparseable(url)

        val remainder = authorityAndRest.removePrefix(authority)
        val beforeFragment = remainder.substringBefore('#')
        val fragment = remainder.substringAfter('#', "").ifEmpty { null }
        val path = beforeFragment.substringBefore('?').ifEmpty { "/" }
        val query = beforeFragment.substringAfter('?', "").ifEmpty { null }

        val isIpLiteral = isIpv4(host) || isIpv6(host)
        val labels = if (isIpLiteral) emptyList() else host.split('.').filter { it.isNotEmpty() }
        val registrableDomain = if (isIpLiteral) host else registrableDomainOf(labels)
        val subdomainLabels = subdomainLabelsOf(labels, registrableDomain)

        val findings = buildSet {
            if (isIpLiteral) {
                add(UrlFinding.IP_LITERAL_HOST)
                if (isPrivateIp(host)) add(UrlFinding.PRIVATE_IP_HOST)
            }
            if (!isIpLiteral && registrableDomain in SHORTENERS) add(UrlFinding.SHORTENER)
            if (labels.any { it.startsWith(PUNYCODE_PREFIX) }) add(UrlFinding.PUNYCODE_LABEL)
            if (host.any { it.code > 127 }) add(UrlFinding.NON_ASCII_HOST)
            if (subdomainLabels.size >= EXCESSIVE_SUBDOMAIN_THRESHOLD) {
                add(UrlFinding.EXCESSIVE_SUBDOMAINS)
            }
            if (hasCredentials) add(UrlFinding.EMBEDDED_CREDENTIALS)
            if (EXECUTABLE_EXTENSIONS.any { path.lowercase().endsWith(it) }) {
                add(UrlFinding.EXECUTABLE_DOWNLOAD_PATH)
            }
            if (port != null && port != defaultPortFor(scheme)) add(UrlFinding.NON_STANDARD_PORT)
            if (scheme == "http") add(UrlFinding.INSECURE_SCHEME)
        }

        return UrlAnalysis(
            original = url,
            normalised = buildNormalised(scheme, host, port, path, query, fragment),
            scheme = scheme,
            host = host,
            registrableDomain = registrableDomain,
            port = port,
            path = path,
            query = query,
            fragment = fragment,
            subdomainLabels = subdomainLabels,
            findings = findings,
            isParseable = true,
        )
    }

    override fun observe(
        urls: List<String>,
        claimedOrganisations: List<String>,
    ): List<Observation> {
        val analyses = urls.map { analyse(it) }.filter { it.isParseable }
        if (analyses.isEmpty()) return emptyList()

        val observations = LinkedHashMap<ObservationKind, Observation>()

        analyses.forEach { analysis ->
            structuralObservations(analysis).forEach { observations.putIfAbsent(it.kind, it) }
        }

        mismatchObservation(analyses, claimedOrganisations)?.let {
            observations.putIfAbsent(it.kind, it)
        }

        return observations.values.sortedBy { it.kind.ordinal }
    }

    /**
     * Maps structural findings onto the observation kinds the risk engine already scores.
     * Findings recorded for display only — private IP, plain HTTP — produce nothing here.
     */
    private fun structuralObservations(analysis: UrlAnalysis): List<Observation> = buildList {
        val where = analysis.original

        if (UrlFinding.IP_LITERAL_HOST in analysis.findings) {
            add(
                Observation(
                    kind = ObservationKind.IP_ADDRESS_URL,
                    summary = "Link points at a raw IP address instead of a named site",
                    detail = where,
                ),
            )
        }
        if (UrlFinding.SHORTENER in analysis.findings) {
            add(
                Observation(
                    kind = ObservationKind.URL_SHORTENER,
                    summary = "Link uses a URL shortener, so the real destination is hidden",
                    detail = where,
                ),
            )
        }
        if (UrlFinding.PUNYCODE_LABEL in analysis.findings ||
            UrlFinding.NON_ASCII_HOST in analysis.findings
        ) {
            add(
                Observation(
                    kind = ObservationKind.PUNYCODE_DOMAIN,
                    summary = "Link uses an internationalised domain that can imitate " +
                        "a familiar name",
                    detail = where,
                ),
            )
        }

        /*
         * Each structural property is reported separately.
         *
         * These used to be merged into a single SUSPICIOUS_URL observation, which meant the
         * risk engine — which deduplicates by kind — could only ever score them once. A link
         * burying a brand in deep subdomains, serving an installable file, and using an odd
         * port scored identically to a link doing any one of those. They are independent
         * facts about a URL and are now weighed independently.
         */
        analysis.findings.forEach { finding ->
            kindFor(finding)?.let { kind ->
                add(Observation(kind = kind, summary = describe(finding), detail = where))
            }
        }
    }

    /**
     * The observation kind for a structural finding, or null for findings that are recorded
     * for display only and deliberately contribute no risk of their own.
     */
    private fun kindFor(finding: UrlFinding): ObservationKind? = when (finding) {
        UrlFinding.EXCESSIVE_SUBDOMAINS -> ObservationKind.DEEP_SUBDOMAIN
        UrlFinding.EMBEDDED_CREDENTIALS -> ObservationKind.EMBEDDED_CREDENTIALS
        UrlFinding.EXECUTABLE_DOWNLOAD_PATH -> ObservationKind.EXECUTABLE_DOWNLOAD_PATH
        UrlFinding.NON_STANDARD_PORT -> ObservationKind.NON_STANDARD_PORT
        // Handled above with their own summaries.
        UrlFinding.IP_LITERAL_HOST, UrlFinding.SHORTENER,
        UrlFinding.PUNYCODE_LABEL, UrlFinding.NON_ASCII_HOST,
        -> null
        // Display only: being on a private range or plain HTTP is not evidence of malice.
        UrlFinding.PRIVATE_IP_HOST, UrlFinding.INSECURE_SCHEME -> null
    }

    private fun describe(finding: UrlFinding): String = when (finding) {
        UrlFinding.EXCESSIVE_SUBDOMAINS -> "Link uses unusually deep subdomains"
        UrlFinding.EMBEDDED_CREDENTIALS ->
            "Link hides its real destination behind an @ sign"
        UrlFinding.EXECUTABLE_DOWNLOAD_PATH -> "Link points at an installable file"
        UrlFinding.NON_STANDARD_PORT -> "Link uses a non-standard port"
        else -> finding.name
    }

    /**
     * Conservative mismatch detection.
     *
     * Only fires when an official domain is actually known for the claimed organisation. A
     * brand name appearing in a *subdomain* never makes a domain official — that is precisely
     * the deception being looked for — so the comparison is against the registrable domain.
     */
    private fun mismatchObservation(
        analyses: List<UrlAnalysis>,
        claimedOrganisations: List<String>,
    ): Observation? {
        claimedOrganisations.forEach { organisation ->
            val official = OFFICIAL_DOMAINS[organisation] ?: return@forEach

            val offending = analyses.firstOrNull { analysis ->
                val domain = analysis.registrableDomain ?: return@firstOrNull false
                official.none { domain == it || domain.endsWith(".$it") }
            } ?: return@forEach

            return Observation(
                kind = ObservationKind.DOMAIN_ORGANISATION_MISMATCH,
                summary = "Message claims to be from $organisation, but the link goes to " +
                    "${offending.registrableDomain}, which is not a $organisation domain",
                detail = offending.original,
            )
        }
        return null
    }

    // --- Parsing helpers --------------------------------------------------------------

    private fun unparseable(original: String) = UrlAnalysis(
        original = original,
        normalised = original.trim(),
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

    /** Returns host (lower-cased, trailing dot removed) and port, or null if malformed. */
    private fun splitHostAndPort(hostAndPort: String): Pair<String, Int?>? {
        if (hostAndPort.startsWith("[")) {
            val close = hostAndPort.indexOf(']')
            if (close < 0) return null
            val host = hostAndPort.substring(0, close + 1).lowercase()
            val portPart = hostAndPort.substring(close + 1).removePrefix(":")
            return host to portPart.toIntOrNull()
        }

        val host = hostAndPort.substringBefore(':').lowercase().trimEnd('.')
        if (host.isEmpty() || host.any { it.isWhitespace() }) return null

        val portText = hostAndPort.substringAfter(':', "")
        val port = if (portText.isEmpty()) null else portText.toIntOrNull() ?: return null
        return host to port
    }

    private fun buildNormalised(
        scheme: String?,
        host: String,
        port: Int?,
        path: String,
        query: String?,
        fragment: String?,
    ): String = buildString {
        if (scheme != null) append("$scheme://")
        append(host)
        // Credentials are intentionally dropped from the display form; the
        // EMBEDDED_CREDENTIALS finding is what tells the user they were there.
        if (port != null && port != defaultPortFor(scheme)) append(":$port")
        append(path)
        if (query != null) append("?$query")
        if (fragment != null) append("#$fragment")
    }

    private fun defaultPortFor(scheme: String?): Int? = when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> null
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.all(Char::isDigit) && (part.toIntOrNull() ?: 256) in 0..255
        }
    }

    private fun isIpv6(host: String): Boolean =
        host.startsWith("[") && host.endsWith("]") && ':' in host

    private fun isPrivateIp(host: String): Boolean {
        if (isIpv6(host)) {
            val inner = host.removeSurrounding("[", "]")
            return inner == "::1" || inner.startsWith("fc") || inner.startsWith("fd")
        }
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return when {
            parts[0] == 10 -> true
            parts[0] == 127 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            parts[0] == 169 && parts[1] == 254 -> true
            else -> false
        }
    }

    /**
     * Approximates the registrable domain using a small multi-part suffix table. This is not
     * the Public Suffix List, so unusual suffixes fall back to the last two labels.
     */
    private fun registrableDomainOf(labels: List<String>): String? {
        if (labels.size < 2) return labels.firstOrNull()

        val lastTwo = labels.takeLast(2).joinToString(".")
        val take = if (lastTwo in MULTI_PART_SUFFIXES && labels.size >= 3) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    private fun subdomainLabelsOf(labels: List<String>, registrable: String?): List<String> {
        if (registrable == null) return emptyList()
        val registrableLabelCount = registrable.split('.').size
        return labels.dropLast(registrableLabelCount)
    }

    private companion object {
        val TRAILING_PUNCTUATION =
            charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

        val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*)://""")

        const val PUNYCODE_PREFIX = "xn--"
        const val EXCESSIVE_SUBDOMAIN_THRESHOLD = 3

        val EXECUTABLE_EXTENSIONS =
            listOf(".apk", ".exe", ".msi", ".bat", ".scr", ".dmg", ".jar", ".apks", ".xapk")

        val SUSPICIOUS_STRUCTURE = setOf(
            UrlFinding.EXCESSIVE_SUBDOMAINS,
            UrlFinding.EMBEDDED_CREDENTIALS,
            UrlFinding.EXECUTABLE_DOWNLOAD_PATH,
            UrlFinding.NON_STANDARD_PORT,
        )

        val MULTI_PART_SUFFIXES = setOf(
            "co.in", "net.in", "org.in", "gov.in", "ac.in", "edu.in", "firm.in", "res.in",
            "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk",
            "com.au", "net.au", "org.au", "com.br", "com.sg", "com.my", "com.hk",
            "co.jp", "co.za", "co.nz", "co.id", "com.tr", "com.mx", "com.ar", "com.cn",
        )

        /** Conservative allowlist. Recognising a shortener never expands it. */
        val SHORTENERS = setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly",
            "rebrand.ly", "cutt.ly", "shorturl.at", "rb.gy", "bit.do", "tiny.cc",
            "lnkd.in", "t.ly", "s.id", "v.gd", "shorte.st", "adf.ly", "bl.ink",
            "trib.al", "po.st", "x.co", "u.to", "clck.ru", "surl.li", "short.io",
        )

        /**
         * Official domains for organisations the extractor can name. Mismatch detection is
         * only possible for entries listed here; anything absent produces no signal at all.
         */
        val OFFICIAL_DOMAINS: Map<String, Set<String>> = mapOf(
            "SBI" to setOf("onlinesbi.sbi", "sbi.co.in", "onlinesbi.com", "bank.sbi"),
            "HDFC Bank" to setOf("hdfcbank.com", "hdfc.com"),
            "ICICI Bank" to setOf("icicibank.com"),
            "Axis Bank" to setOf("axisbank.com", "axisbank.co.in"),
            "Kotak Mahindra Bank" to setOf("kotak.com"),
            "Punjab National Bank" to setOf("pnbindia.in"),
            "Bank of Baroda" to setOf("bankofbaroda.in", "bankofbaroda.co.in"),
            "Canara Bank" to setOf("canarabank.com", "canarabank.in"),
            "Union Bank" to setOf("unionbankofindia.co.in"),
            "Yes Bank" to setOf("yesbank.in"),
            "IDFC First Bank" to setOf("idfcfirstbank.com"),
            "IndusInd Bank" to setOf("indusind.com"),
            "RBI" to setOf("rbi.org.in"),
            "Paytm" to setOf("paytm.com", "paytmbank.com"),
            "PhonePe" to setOf("phonepe.com"),
            "Google Pay" to setOf("google.com", "pay.google.com"),
            "Amazon" to setOf("amazon.in", "amazon.com"),
            "Flipkart" to setOf("flipkart.com"),
            "IRCTC" to setOf("irctc.co.in"),
            "EPFO" to setOf("epfindia.gov.in"),
            "UIDAI" to setOf("uidai.gov.in"),
            "Income Tax Department" to setOf("incometax.gov.in"),
            "Airtel" to setOf("airtel.in"),
            "Jio" to setOf("jio.com"),
            "India Post" to setOf("indiapost.gov.in"),
            "Blue Dart" to setOf("bluedart.com"),
            "FedEx" to setOf("fedex.com"),
            "Netflix" to setOf("netflix.com"),
        )
    }
}
