package com.trustlens.investigation

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.IndicatorKind
import org.junit.Test

class RuleBasedIndicatorExtractorTest {

    private val extractor = RuleBasedIndicatorExtractor()

    // --- Empty and blank input --------------------------------------------------------

    @Test
    fun `empty input yields no indicators`() {
        val result = extractor.extract("")

        assertThat(result.urls).isEmpty()
        assertThat(result.domains).isEmpty()
        assertThat(result.phoneNumbers).isEmpty()
        assertThat(result.upiIds).isEmpty()
        assertThat(result.amounts).isEmpty()
        assertThat(result.organisations).isEmpty()
        assertThat(result.urgency).isFalse()
        assertThat(result.otpRequest).isFalse()
        assertThat(result.paymentRequest).isFalse()
        assertThat(result.credentialRequest).isFalse()
        assertThat(result.accountThreat).isFalse()
        assertThat(result.impersonatedOrganisation).isNull()
        assertThat(result.matches).isEmpty()
    }

    @Test
    fun `whitespace only input yields no indicators`() {
        assertThat(extractor.extract("   \n\t  ").matches).isEmpty()
    }

    // --- URLs and domains -------------------------------------------------------------

    @Test
    fun `extracts an https url and its domain`() {
        val result = extractor.extract("Verify at https://sbi-kyc.example.com/verify now")

        assertThat(result.urls).containsExactly("https://sbi-kyc.example.com/verify")
        assertThat(result.domains).containsExactly("sbi-kyc.example.com")
    }

    @Test
    fun `extracts an http url`() {
        val result = extractor.extract("Go to http://bad.example.in/pay")

        assertThat(result.urls).containsExactly("http://bad.example.in/pay")
    }

    @Test
    fun `extracts a bare www url`() {
        val result = extractor.extract("Visit www.fake-bank.com today")

        assertThat(result.urls).containsExactly("www.fake-bank.com")
        assertThat(result.domains).containsExactly("www.fake-bank.com")
    }

    @Test
    fun `extracts a bare domain with a known tld`() {
        val result = extractor.extract("Login at sbi-verify.top immediately")

        assertThat(result.urls).contains("sbi-verify.top")
    }

    @Test
    fun `extracts a bare domain on a free registration tld`() {
        // Omitting these meant a pasted `sbi.bank.tk/verify` was not recognised as a link at
        // all, so it reached neither URL analysis nor a reputation lookup — while the same
        // string inside a QR code did.
        val result = extractor.extract("Dear customer, verify at sbi.bank.tk/verify today")

        assertThat(result.urls).contains("sbi.bank.tk/verify")
        assertThat(result.domains).contains("sbi.bank.tk")
    }

    @Test
    fun `extracts every free registration tld as a bare domain`() {
        listOf(
            "freerecharge.ml/offer",
            "claim.tk/now",
            "offer.ga/x",
            "promo.cf/y",
            "deal.gq/z",
        ).forEach { link ->
            assertThat(extractor.extract("Open $link now").urls).contains(link)
        }
    }

    @Test
    fun `strips trailing sentence punctuation from a url`() {
        val result = extractor.extract("Open https://example.com/verify.")

        assertThat(result.urls).containsExactly("https://example.com/verify")
    }

    @Test
    fun `a url is not double counted as a bare domain`() {
        val result = extractor.extract("https://example.com/path")

        assertThat(result.urls).hasSize(1)
    }

    @Test
    fun `url host is lowercased in domains but the url text is preserved verbatim`() {
        val result = extractor.extract("Go to HTTPS://Example.COM/Path")

        assertThat(result.urls).containsExactly("HTTPS://Example.COM/Path")
        assertThat(result.domains).containsExactly("example.com")
    }

    @Test
    fun `extracts a url with a port and strips it from the domain`() {
        val result = extractor.extract("http://192.0.2.10:8080/pay")

        assertThat(result.domains).containsExactly("192.0.2.10")
    }

    // --- Phone numbers ----------------------------------------------------------------

    @Test
    fun `extracts an indian mobile number with country code`() {
        val result = extractor.extract("Call +91 98765 43210 now")

        assertThat(result.phoneNumbers).containsExactly("+91 98765 43210")
    }

    @Test
    fun `extracts a bare ten digit indian mobile number`() {
        val result = extractor.extract("Contact 9876543210 for help")

        assertThat(result.phoneNumbers).containsExactly("9876543210")
    }

    // --- UPI and email ----------------------------------------------------------------

    @Test
    fun `extracts a upi id`() {
        val result = extractor.extract("Pay to fraudster@ybl right now")

        assertThat(result.upiIds).containsExactly("fraudster@ybl")
    }

    @Test
    fun `extracts a numeric upi id`() {
        val result = extractor.extract("Send to 9876543210@paytm")

        assertThat(result.upiIds).containsExactly("9876543210@paytm")
    }

    @Test
    fun `an email address is not treated as a upi id`() {
        val result = extractor.extract("Write to support@example.com for help")

        assertThat(result.upiIds).isEmpty()
        assertThat(result.emails).containsExactly("support@example.com")
    }

    // --- Amounts ----------------------------------------------------------------------

    @Test
    fun `extracts a rupee symbol amount preserving source text`() {
        val result = extractor.extract("Pay ₹4,999 immediately")

        assertThat(result.amounts).containsExactly("₹4,999")
    }

    @Test
    fun `extracts an Rs prefixed amount`() {
        val result = extractor.extract("Transfer Rs. 5000 now")

        assertThat(result.amounts).containsExactly("Rs. 5000")
    }

    @Test
    fun `extracts a trailing rupees amount`() {
        val result = extractor.extract("You owe 2500 rupees")

        assertThat(result.amounts).containsExactly("2500 rupees")
    }

    // --- Organisations and impersonation ----------------------------------------------

    @Test
    fun `extracts a known bank name`() {
        val result = extractor.extract("Your SBI account needs attention")

        assertThat(result.organisations).contains("SBI")
    }

    @Test
    fun `flags impersonation when an organisation appears with a coercive request`() {
        val result = extractor.extract(
            "Your SBI account will be blocked. Share your OTP immediately.",
        )

        assertThat(result.impersonatedOrganisation).isEqualTo("SBI")
    }

    @Test
    fun `an organisation mentioned without any coercive signal is not impersonation`() {
        val result = extractor.extract("Your SBI monthly statement is ready to download.")

        assertThat(result.organisations).contains("SBI")
        assertThat(result.impersonatedOrganisation).isNull()
    }

    // --- Request and threat flags -----------------------------------------------------

    @Test
    fun `detects urgency language`() {
        assertThat(extractor.extract("Act immediately or lose access").urgency).isTrue()
    }

    @Test
    fun `detects an otp request`() {
        assertThat(extractor.extract("Tell me your OTP right now").otpRequest).isTrue()
    }

    @Test
    fun `detects a credential request`() {
        val result = extractor.extract("Enter your net banking password to continue")

        assertThat(result.credentialRequest).isTrue()
    }

    @Test
    fun `detects a payment request`() {
        assertThat(extractor.extract("Please transfer the money now").paymentRequest).isTrue()
    }

    @Test
    fun `detects an account threat`() {
        assertThat(extractor.extract("Your account will be blocked today").accountThreat).isTrue()
    }

    @Test
    fun `records requested actions`() {
        val result = extractor.extract("Complete your KYC and share the OTP to continue")

        assertThat(result.requestedActions).contains("complete_kyc")
        assertThat(result.requestedActions).contains("share_otp")
    }

    // --- False positives and edge cases -----------------------------------------------

    @Test
    fun `a genuine bank otp delivery is not an otp request`() {
        val result = extractor.extract(
            "123456 is your OTP for a transaction of Rs. 5000. Do not share it with anyone.",
        )

        assertThat(result.otpRequest).isFalse()
    }

    @Test
    fun `a do not share warning is not a credential request`() {
        val result = extractor.extract("Bank staff will never ask for your password or PIN.")

        assertThat(result.credentialRequest).isFalse()
    }

    @Test
    fun `a six digit otp code is not read as a phone number`() {
        assertThat(extractor.extract("Your code is 123456").phoneNumbers).isEmpty()
    }

    @Test
    fun `a sixteen digit card number is not read as a phone number`() {
        assertThat(extractor.extract("Card 4111111111111111 was used").phoneNumbers).isEmpty()
    }

    @Test
    fun `a landline style number starting with one is not read as a mobile`() {
        assertThat(extractor.extract("Dial 1234567890 please").phoneNumbers).isEmpty()
    }

    @Test
    fun `a sentence boundary is not mistaken for a domain`() {
        val result = extractor.extract("Your account will be blocked. Do not delay.")

        assertThat(result.urls).isEmpty()
        assertThat(result.domains).isEmpty()
    }

    @Test
    fun `a decimal amount is not mistaken for a domain`() {
        val result = extractor.extract("The fee is Rs.500 only")

        assertThat(result.urls).isEmpty()
    }

    @Test
    fun `an ordinary delivery message raises no coercion flags`() {
        val result = extractor.extract("Your order has been shipped and arrives soon.")

        assertThat(result.urgency).isFalse()
        assertThat(result.otpRequest).isFalse()
        assertThat(result.paymentRequest).isFalse()
        assertThat(result.credentialRequest).isFalse()
        assertThat(result.accountThreat).isFalse()
    }

    // --- Malformed input --------------------------------------------------------------

    @Test
    fun `a bare scheme with no host produces no url`() {
        assertThat(extractor.extract("http://").urls).isEmpty()
    }

    @Test
    fun `punctuation soup does not crash and yields nothing`() {
        val result = extractor.extract("@@@ ::: /// ... ??? ###")

        assertThat(result.upiIds).isEmpty()
        assertThat(result.urls).isEmpty()
    }

    @Test
    fun `unicode and emoji input is handled safely`() {
        val result = extractor.extract("नमस्ते 🎉 आपका खाता ब्लॉक हो जाएगा ₹5000")

        assertThat(result.amounts).containsExactly("₹5000")
    }

    @Test
    fun `very long input is processed without failure`() {
        val long = "urgent ".repeat(5000) + "https://example.com"

        val result = extractor.extract(long)

        assertThat(result.urgency).isTrue()
        assertThat(result.urls).contains("https://example.com")
    }

    @Test
    fun `an at sign with no local part is not a upi id`() {
        assertThat(extractor.extract("Follow @paytm for updates").upiIds).isEmpty()
    }

    // --- Multiple indicators ----------------------------------------------------------

    @Test
    fun `a full phishing message yields every expected indicator`() {
        val result = extractor.extract(
            "URGENT: Your SBI account will be blocked today. " +
                "Complete KYC at https://sbi-kyc.example.com/verify, " +
                "share your OTP, or pay Rs. 4999 to fraudster@ybl. Call 9876543210.",
        )

        assertThat(result.urls).contains("https://sbi-kyc.example.com/verify")
        assertThat(result.domains).contains("sbi-kyc.example.com")
        assertThat(result.upiIds).contains("fraudster@ybl")
        assertThat(result.phoneNumbers).contains("9876543210")
        assertThat(result.amounts).contains("Rs. 4999")
        assertThat(result.organisations).contains("SBI")
        assertThat(result.urgency).isTrue()
        assertThat(result.otpRequest).isTrue()
        assertThat(result.paymentRequest).isTrue()
        assertThat(result.accountThreat).isTrue()
        assertThat(result.impersonatedOrganisation).isEqualTo("SBI")
    }

    @Test
    fun `multiple distinct urls are all captured without duplicates`() {
        val result = extractor.extract(
            "Try https://a.example.com then https://b.example.com then https://a.example.com",
        )

        assertThat(result.urls).containsExactly("https://a.example.com", "https://b.example.com")
    }

    // --- Traceability -----------------------------------------------------------------

    @Test
    fun `every match points at the exact source text it came from`() {
        val text = "Pay ₹4,999 to fraudster@ybl at https://example.com"

        val result = extractor.extract(text)

        assertThat(result.matches).isNotEmpty()
        result.matches.forEach { match ->
            assertThat(text.substring(match.startIndex, match.endIndex)).isEqualTo(match.text)
        }
    }

    @Test
    fun `matches record the kind of indicator found`() {
        val result = extractor.extract("Pay ₹4,999 to fraudster@ybl")

        assertThat(result.matches.map { it.kind })
            .containsAtLeast(IndicatorKind.AMOUNT, IndicatorKind.UPI_ID)
    }

    @Test
    fun `a url is preserved verbatim and is never resolved or normalised away`() {
        val text = "Open https://example.com/a%20b?x=1&y=2#frag"

        val result = extractor.extract(text)

        assertThat(result.urls).containsExactly("https://example.com/a%20b?x=1&y=2#frag")
    }

    // --- Determinism ------------------------------------------------------------------

    @Test
    fun `the same input always produces an identical result`() {
        val text = "URGENT: SBI account blocked. Share OTP, pay Rs. 999 to a@ybl, " +
            "call 9876543210, visit https://x.example.com"

        val results = (1..50).map { extractor.extract(text) }

        assertThat(results.distinct()).hasSize(1)
    }

    @Test
    fun `a fresh extractor instance produces the same result as another`() {
        val text = "Share your OTP at https://example.com"

        assertThat(RuleBasedIndicatorExtractor().extract(text))
            .isEqualTo(RuleBasedIndicatorExtractor().extract(text))
    }

    @Test
    fun `results preserve first appearance order`() {
        val result = extractor.extract("https://b.example.com and https://a.example.com")

        assertThat(result.urls).containsExactly("https://b.example.com", "https://a.example.com")
            .inOrder()
    }
}
