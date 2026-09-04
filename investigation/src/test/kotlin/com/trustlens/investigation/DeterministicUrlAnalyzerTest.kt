package com.trustlens.investigation

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.ObservationKind
import com.trustlens.domain.UrlFinding
import org.junit.Test

class DeterministicUrlAnalyzerTest {

    private val analyzer = DeterministicUrlAnalyzer()

    // --- Normal URLs ------------------------------------------------------------------

    @Test
    fun `parses a normal https url`() {
        val result = analyzer.analyse("https://www.example.com/account")

        assertThat(result.isParseable).isTrue()
        assertThat(result.scheme).isEqualTo("https")
        assertThat(result.host).isEqualTo("www.example.com")
        assertThat(result.registrableDomain).isEqualTo("example.com")
        assertThat(result.path).isEqualTo("/account")
        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `parses a plain http url and records the insecure scheme`() {
        val result = analyzer.analyse("http://example.com/login")

        assertThat(result.scheme).isEqualTo("http")
        assertThat(result.findings).contains(UrlFinding.INSECURE_SCHEME)
    }

    @Test
    fun `parses a bare domain with no scheme`() {
        val result = analyzer.analyse("example.com/verify")

        assertThat(result.isParseable).isTrue()
        assertThat(result.scheme).isNull()
        assertThat(result.host).isEqualTo("example.com")
        assertThat(result.registrableDomain).isEqualTo("example.com")
    }

    @Test
    fun `splits path query and fragment`() {
        val result = analyzer.analyse("https://example.com/a/b?token=1&x=2#section")

        assertThat(result.path).isEqualTo("/a/b")
        assertThat(result.query).isEqualTo("token=1&x=2")
        assertThat(result.fragment).isEqualTo("section")
    }

    @Test
    fun `handles a url with no path`() {
        val result = analyzer.analyse("https://example.com")

        assertThat(result.host).isEqualTo("example.com")
        assertThat(result.path).isEqualTo("/")
    }

    @Test
    fun `resolves a multi part public suffix`() {
        val result = analyzer.analyse("https://portal.onlinesbi.co.in/login")

        assertThat(result.registrableDomain).isEqualTo("onlinesbi.co.in")
        assertThat(result.subdomainLabels).containsExactly("portal")
    }

    // --- Normalisation ----------------------------------------------------------------

    @Test
    fun `lowercases the hostname but preserves path case`() {
        val result = analyzer.analyse("HTTPS://Example.COM/MyPath")

        assertThat(result.host).isEqualTo("example.com")
        assertThat(result.path).isEqualTo("/MyPath")
        assertThat(result.normalised).isEqualTo("https://example.com/MyPath")
    }

    @Test
    fun `drops a default port but keeps a non standard one`() {
        assertThat(analyzer.analyse("https://example.com:443/x").normalised)
            .isEqualTo("https://example.com/x")
        assertThat(analyzer.analyse("https://example.com:8443/x").normalised)
            .isEqualTo("https://example.com:8443/x")
    }

    @Test
    fun `strips a trailing dot from the hostname`() {
        assertThat(analyzer.analyse("https://example.com./x").host).isEqualTo("example.com")
    }

    @Test
    fun `strips trailing sentence punctuation`() {
        assertThat(analyzer.analyse("https://example.com/verify.").host).isEqualTo("example.com")
        assertThat(analyzer.analyse("https://example.com/verify,").path).isEqualTo("/verify")
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertThat(analyzer.analyse("  https://example.com/x  ").host).isEqualTo("example.com")
    }

    @Test
    fun `preserves the original text verbatim`() {
        val raw = "HTTPS://Example.COM/a%20b?x=1#f"

        assertThat(analyzer.analyse(raw).original).isEqualTo(raw)
    }

    // --- Shorteners -------------------------------------------------------------------

    @Test
    fun `recognises a known url shortener`() {
        val result = analyzer.analyse("https://bit.ly/3xYzAbc")

        assertThat(result.findings).contains(UrlFinding.SHORTENER)
    }

    @Test
    fun `recognises other common shorteners`() {
        listOf("https://tinyurl.com/abc", "https://t.co/abc", "https://cutt.ly/abc")
            .forEach { url ->
                assertThat(analyzer.analyse(url).findings).contains(UrlFinding.SHORTENER)
            }
    }

    @Test
    fun `an ordinary domain is not treated as a shortener`() {
        assertThat(analyzer.analyse("https://example.com/abc").findings)
            .doesNotContain(UrlFinding.SHORTENER)
    }

    @Test
    fun `a shortener is a signal for investigation and not an automatic scam verdict`() {
        val observations = analyzer.observe(listOf("https://bit.ly/abc"), emptyList())

        assertThat(observations.map { it.kind }).containsExactly(ObservationKind.URL_SHORTENER)
        // Nothing here asserts malice; the shortener alone is only a prompt to investigate.
        assertThat(observations.map { it.kind })
            .doesNotContain(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `a shortened url is never expanded`() {
        val result = analyzer.analyse("https://bit.ly/3xYzAbc")

        // The host stays the shortener; nothing was followed to discover a destination.
        assertThat(result.host).isEqualTo("bit.ly")
        assertThat(result.normalised).isEqualTo("https://bit.ly/3xYzAbc")
    }

    // --- IP literal hosts -------------------------------------------------------------

    @Test
    fun `detects an ipv4 literal host`() {
        val result = analyzer.analyse("http://203.0.113.10/verify")

        assertThat(result.findings).contains(UrlFinding.IP_LITERAL_HOST)
        assertThat(result.host).isEqualTo("203.0.113.10")
        assertThat(result.registrableDomain).isEqualTo("203.0.113.10")
    }

    @Test
    fun `detects a private ipv4 host without calling it malicious`() {
        val result = analyzer.analyse("http://192.168.1.10/login")

        assertThat(result.findings).contains(UrlFinding.IP_LITERAL_HOST)
        assertThat(result.findings).contains(UrlFinding.PRIVATE_IP_HOST)

        // Being private adds no risk observation of its own.
        val observations = analyzer.observe(listOf("http://192.168.1.10/login"), emptyList())
        val public = analyzer.observe(listOf("http://203.0.113.10/login"), emptyList())
        assertThat(observations.map { it.kind }).isEqualTo(public.map { it.kind })
    }

    @Test
    fun `detects an ipv6 literal host`() {
        val result = analyzer.analyse("http://[2001:db8::1]:8080/verify")

        assertThat(result.findings).contains(UrlFinding.IP_LITERAL_HOST)
        assertThat(result.host).isEqualTo("[2001:db8::1]")
        assertThat(result.port).isEqualTo(8080)
    }

    @Test
    fun `a dotted number that is not a valid ipv4 is not an ip host`() {
        val result = analyzer.analyse("http://999.999.1.1/x")

        assertThat(result.findings).doesNotContain(UrlFinding.IP_LITERAL_HOST)
    }

    @Test
    fun `an ip literal produces the IP_ADDRESS_URL observation`() {
        val observations = analyzer.observe(listOf("http://203.0.113.10/verify"), emptyList())

        assertThat(observations.map { it.kind }).contains(ObservationKind.IP_ADDRESS_URL)
    }

    // --- Punycode and homograph -------------------------------------------------------

    @Test
    fun `detects a punycode label`() {
        val result = analyzer.analyse("https://xn--80ak6aa92e.com/login")

        assertThat(result.findings).contains(UrlFinding.PUNYCODE_LABEL)
    }

    @Test
    fun `detects punycode in a subdomain`() {
        val result = analyzer.analyse("https://xn--pple-43d.example.com/x")

        assertThat(result.findings).contains(UrlFinding.PUNYCODE_LABEL)
    }

    @Test
    fun `punycode is never decoded`() {
        val result = analyzer.analyse("https://xn--80ak6aa92e.com/login")

        // Left exactly as written: no Unicode resolution, no lookup.
        assertThat(result.host).isEqualTo("xn--80ak6aa92e.com")
    }

    @Test
    fun `detects a non ascii hostname as a homograph indicator`() {
        val result = analyzer.analyse("https://аpple.com/login")

        assertThat(result.findings).contains(UrlFinding.NON_ASCII_HOST)
    }

    @Test
    fun `punycode produces the PUNYCODE_DOMAIN observation`() {
        val observations = analyzer.observe(listOf("https://xn--80ak6aa92e.com/x"), emptyList())

        assertThat(observations.map { it.kind }).contains(ObservationKind.PUNYCODE_DOMAIN)
    }

    // --- Suspicious structure ---------------------------------------------------------

    @Test
    fun `flags excessive subdomains`() {
        val result = analyzer.analyse("https://sbi.secure.login.verify.example.com/x")

        assertThat(result.findings).contains(UrlFinding.EXCESSIVE_SUBDOMAINS)
    }

    @Test
    fun `an ordinary www subdomain is not excessive`() {
        assertThat(analyzer.analyse("https://www.example.com/x").findings)
            .doesNotContain(UrlFinding.EXCESSIVE_SUBDOMAINS)
    }

    @Test
    fun `flags credentials embedded in the authority`() {
        val result = analyzer.analyse("https://onlinesbi.sbi@evil.example.com/login")

        assertThat(result.findings).contains(UrlFinding.EMBEDDED_CREDENTIALS)
        // The real host is what follows the @, which is the whole point of the trick.
        assertThat(result.host).isEqualTo("evil.example.com")
    }

    @Test
    fun `flags an executable download path`() {
        val result = analyzer.analyse("http://example.com/update.apk")

        assertThat(result.findings).contains(UrlFinding.EXECUTABLE_DOWNLOAD_PATH)
    }

    @Test
    fun `flags a non standard port`() {
        assertThat(analyzer.analyse("https://example.com:8080/x").findings)
            .contains(UrlFinding.NON_STANDARD_PORT)
    }

    @Test
    fun `deep subdomain nesting produces its own observation`() {
        val observations = analyzer.observe(
            listOf("https://sbi.secure.login.verify.example.com/x"),
            emptyList(),
        )

        assertThat(observations.map { it.kind }).contains(ObservationKind.DEEP_SUBDOMAIN)
    }

    @Test
    fun `each structural property is reported separately, not merged`() {
        // Phase 11 calibration: these three used to collapse into a single SUSPICIOUS_URL
        // observation, which the risk engine could then only score once. They are
        // independent facts about the link and are now reported independently.
        val observations = analyzer.observe(
            listOf("https://sbi.secure.login.example.com:8443/kyc/update.apk"),
            emptyList(),
        )

        assertThat(observations.map { it.kind }).containsAtLeast(
            ObservationKind.DEEP_SUBDOMAIN,
            ObservationKind.EXECUTABLE_DOWNLOAD_PATH,
            ObservationKind.NON_STANDARD_PORT,
        )
    }

    @Test
    fun `embedded credentials produce their own observation`() {
        val observations = analyzer.observe(
            listOf("https://sbi.example.com@evil.example.net/login"),
            emptyList(),
        )

        assertThat(observations.map { it.kind })
            .contains(ObservationKind.EMBEDDED_CREDENTIALS)
    }

    @Test
    fun `a clean url produces no observations`() {
        assertThat(analyzer.observe(listOf("https://www.example.com/help"), emptyList()))
            .isEmpty()
    }

    // --- Organisation mismatch --------------------------------------------------------

    @Test
    fun `flags a mismatch when a claimed bank links to an unrelated domain`() {
        val observations = analyzer.observe(
            listOf("https://sbi-kyc.example.com/verify"),
            listOf("SBI"),
        )

        assertThat(observations.map { it.kind })
            .contains(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `a brand name in a subdomain does not make the domain official`() {
        val observations = analyzer.observe(
            listOf("https://onlinesbi.sbi.evil.example.com/verify"),
            listOf("SBI"),
        )

        assertThat(observations.map { it.kind })
            .contains(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `the genuine organisation domain is not a mismatch`() {
        val observations = analyzer.observe(
            listOf("https://onlinesbi.sbi/personal"),
            listOf("SBI"),
        )

        assertThat(observations.map { it.kind })
            .doesNotContain(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `a subdomain of the genuine organisation domain is not a mismatch`() {
        val observations = analyzer.observe(
            listOf("https://retail.onlinesbi.sbi/login"),
            listOf("SBI"),
        )

        assertThat(observations.map { it.kind })
            .doesNotContain(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `no mismatch is claimed for an organisation we have no official domain for`() {
        val observations = analyzer.observe(
            listOf("https://example.com/x"),
            listOf("Some Unlisted Company"),
        )

        // Unknown is not suspicious: without a reference domain we cannot judge.
        assertThat(observations.map { it.kind })
            .doesNotContain(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `no mismatch is claimed when no organisation was mentioned`() {
        val observations = analyzer.observe(listOf("https://example.com/x"), emptyList())

        assertThat(observations.map { it.kind })
            .doesNotContain(ObservationKind.DOMAIN_ORGANISATION_MISMATCH)
    }

    @Test
    fun `an ordinary third party domain is not flagged without an organisation claim`() {
        val observations = analyzer.observe(
            listOf("https://news.example.org/article"),
            emptyList(),
        )

        assertThat(observations).isEmpty()
    }

    // --- Malformed input --------------------------------------------------------------

    @Test
    fun `an empty string is not parseable`() {
        assertThat(analyzer.analyse("").isParseable).isFalse()
    }

    @Test
    fun `a bare scheme is not parseable`() {
        assertThat(analyzer.analyse("http://").isParseable).isFalse()
    }

    @Test
    fun `punctuation soup is not parseable and does not crash`() {
        listOf(":::", "///", "http:///", "@@@", "  ").forEach {
            assertThat(analyzer.analyse(it).isParseable).isFalse()
        }
    }

    @Test
    fun `unparseable urls produce no observations`() {
        assertThat(analyzer.observe(listOf("http://", ":::"), listOf("SBI"))).isEmpty()
    }

    @Test
    fun `a very long url is handled without failure`() {
        val url = "https://example.com/" + "a".repeat(10_000)

        assertThat(analyzer.analyse(url).host).isEqualTo("example.com")
    }

    // --- Multiple URLs ----------------------------------------------------------------

    @Test
    fun `analyses multiple urls and merges their observations`() {
        val observations = analyzer.observe(
            listOf("https://bit.ly/abc", "http://203.0.113.10/verify"),
            emptyList(),
        )

        assertThat(observations.map { it.kind })
            .containsAtLeast(ObservationKind.URL_SHORTENER, ObservationKind.IP_ADDRESS_URL)
    }

    @Test
    fun `a repeated observation kind is reported once`() {
        val observations = analyzer.observe(
            listOf("https://bit.ly/abc", "https://tinyurl.com/def"),
            emptyList(),
        )

        assertThat(observations.filter { it.kind == ObservationKind.URL_SHORTENER }).hasSize(1)
    }

    // --- Traceability -----------------------------------------------------------------

    @Test
    fun `observations quote the url they came from`() {
        val url = "http://203.0.113.10/verify"
        val observations = analyzer.observe(listOf(url), emptyList())

        val ipObservation = observations.first { it.kind == ObservationKind.IP_ADDRESS_URL }
        assertThat(ipObservation.detail).contains(url)
    }

    @Test
    fun `a mismatch observation names both the organisation and the domain`() {
        val observations = analyzer.observe(
            listOf("https://sbi-kyc.example.com/verify"),
            listOf("SBI"),
        )

        val mismatch = observations
            .first { it.kind == ObservationKind.DOMAIN_ORGANISATION_MISMATCH }
        assertThat(mismatch.summary).contains("SBI")
        assertThat(mismatch.summary).contains("example.com")
    }

    // --- Determinism and inertness ----------------------------------------------------

    @Test
    fun `repeated analysis of the same url is identical`() {
        val url = "https://sbi.secure.login.example.com:8443/verify.apk?x=1"

        assertThat((1..50).map { analyzer.analyse(url) }.distinct()).hasSize(1)
    }

    @Test
    fun `a fresh analyzer produces the same result as another`() {
        val url = "https://bit.ly/abc"

        assertThat(DeterministicUrlAnalyzer().analyse(url))
            .isEqualTo(DeterministicUrlAnalyzer().analyse(url))
    }

    @Test
    fun `analysis of an unreachable host still succeeds because nothing is contacted`() {
        // A host that could never resolve: if anything tried DNS this would fail or hang.
        val result = analyzer.analyse("https://this-host-does-not-exist.invalid/login")

        assertThat(result.isParseable).isTrue()
        assertThat(result.host).isEqualTo("this-host-does-not-exist.invalid")
    }

    @Test
    fun `analysis completes immediately with no IO latency`() {
        val start = System.nanoTime()
        repeat(1000) { analyzer.analyse("https://example.com/a?b=c#d") }
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000

        // Any network call in this path would make 1000 iterations impossible this fast.
        assertThat(elapsedMillis).isLessThan(2000)
    }
}
