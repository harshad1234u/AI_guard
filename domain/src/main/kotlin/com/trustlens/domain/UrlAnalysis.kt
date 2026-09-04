package com.trustlens.domain

/**
 * A structural property detected in a URL by purely offline inspection.
 *
 * Findings describe *shape*, not verdict. Several of them are perfectly ordinary on their own
 * — a shortener, a non-standard port — and only become interesting alongside other signals,
 * which is why weighing them is the risk engine's job and not this layer's.
 */
enum class UrlFinding {
    /** Host is a literal IP address rather than a name. */
    IP_LITERAL_HOST,

    /**
     * Host is an IP in a private/loopback range. Recorded for display only: being private is
     * not itself a sign of malice and contributes no risk of its own.
     */
    PRIVATE_IP_HOST,

    /** Registrable domain belongs to a known URL-shortening service. */
    SHORTENER,

    /** A hostname label begins with the `xn--` IDN prefix. */
    PUNYCODE_LABEL,

    /** Hostname contains non-ASCII characters — a basic homograph indicator. */
    NON_ASCII_HOST,

    /** Unusually deep subdomain nesting, often used to bury a brand name in a fake host. */
    EXCESSIVE_SUBDOMAINS,

    /** Credentials embedded before an `@`, hiding the real host behind a familiar-looking one. */
    EMBEDDED_CREDENTIALS,

    /** Path points at an installable or executable file. */
    EXECUTABLE_DOWNLOAD_PATH,

    /** An explicit port other than the scheme default. */
    NON_STANDARD_PORT,

    /** Plain HTTP. Recorded for display; too common to score on its own. */
    INSECURE_SCHEME,
}

/**
 * The result of inspecting a single URL entirely offline.
 *
 * Nothing in producing this value contacts the network: no DNS, no HTTP, no redirect
 * following and no shortener expansion. [original] is kept verbatim so the user always sees
 * exactly what was in the message, and [normalised] is a display form derived by string
 * manipulation alone.
 */
data class UrlAnalysis(
    val original: String,
    val normalised: String,
    val scheme: String?,
    val host: String?,
    /**
     * The registrable domain (`example.com` for `a.b.example.com`), resolved with a small
     * multi-part suffix table rather than the full Public Suffix List.
     */
    val registrableDomain: String?,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
    val subdomainLabels: List<String>,
    val findings: Set<UrlFinding>,
    /** False when the text could not be read as a URL at all; no findings are reported then. */
    val isParseable: Boolean,
)

/**
 * Offline URL inspection.
 *
 * Implementations must never open, resolve, expand, fetch or otherwise contact a URL. A URL
 * reaching this interface is untrusted input and stays inert data throughout.
 */
interface UrlAnalyzer {

    /** Structural analysis of one URL. */
    fun analyse(url: String): UrlAnalysis

    /**
     * Observations for a whole message: per-URL structural findings, plus any mismatch
     * between an organisation the message claims to be from and the domains it links to.
     *
     * [claimedOrganisations] comes from indicator extraction. Mismatch is only reported when
     * an official domain is actually known for the named organisation — an unrecognised
     * organisation yields no signal, because absence of a reference is not evidence.
     */
    fun observe(urls: List<String>, claimedOrganisations: List<String>): List<Observation>
}
