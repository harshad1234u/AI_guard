package com.trustlens.investigation

import com.google.common.truth.Truth.assertThat
import com.trustlens.domain.Indicators
import org.junit.Test

/**
 * The freeze guard.
 *
 * [MultilingualIndicatorExtractor] sits in front of the extractor every surface uses — Check
 * Message, Scan QR, Lens Scan, Check Email and Live Guard alike — so the only acceptable
 * effect it may have on English content is *none*. This suite is what makes that a fact
 * rather than an intention.
 *
 * The corpus below is deliberately the text the frozen integration tests use, including the
 * demo hero message, plus the cases most likely to be broken by a careless Hindi pattern:
 * TrustLens's own warning wording, a genuine bank notification, and ordinary prose that
 * happens to contain "pin", "band", "do" and "mat".
 */
class MultilingualParityTest {

    private val frozen = RuleBasedIndicatorExtractor()
    private val wrapped = MultilingualIndicatorExtractor(frozen)

    private val englishCorpus = listOf(
        "URGENT: Your SBI account will be blocked today. Complete KYC at " +
            "https://sbi-kyc.example.com/verify and share your OTP, or pay Rs. 4999.",
        "123456 is your OTP for HDFC Bank. Do not share it with anyone.",
        "Your Amazon parcel could not be delivered. Update your address at " +
            "http://amzn-redelivery.example.com/track",
        "Do not send money to fraudster@ybl or share your UPI PIN with anyone.",
        "Previous payment of Rs. 1200 was sent to merchant@okaxis on 4 March.",
        "Hi, are we still meeting for lunch at one o'clock today?",
        "The bowling pin fell over and the band played on. Do what you like with the mat.",
        "Dear customer, your account statement for August is ready in the app.",
        "Call 9876543210 to confirm your Flipkart order before 6 pm.",
        "",
        "   ",
    )

    @Test
    fun `english content reaches identical indicators`() {
        englishCorpus.forEach { text ->
            val before = frozen.extract(text)
            val after = wrapped.extract(text)

            assertThat(after.flags()).isEqualTo(before.flags())
            assertThat(after.urls).isEqualTo(before.urls)
            assertThat(after.domains).isEqualTo(before.domains)
            assertThat(after.upiIds).isEqualTo(before.upiIds)
            assertThat(after.emails).isEqualTo(before.emails)
            assertThat(after.phoneNumbers).isEqualTo(before.phoneNumbers)
            assertThat(after.amounts).isEqualTo(before.amounts)
            assertThat(after.organisations).isEqualTo(before.organisations)
            assertThat(after.impersonatedOrganisation).isEqualTo(before.impersonatedOrganisation)
        }
    }

    @Test
    fun `the demo hero message is untouched`() {
        val hero = englishCorpus.first()

        val before = frozen.extract(hero)
        val after = wrapped.extract(hero)

        // Every scored flag, and the impersonation that drives ORGANISATION_IMPERSONATION.
        assertThat(after.flags()).isEqualTo(before.flags())
        assertThat(after.impersonatedOrganisation).isEqualTo("SBI")
        assertThat(after.matches.map { it.kind to it.text })
            .containsAtLeastElementsIn(before.matches.map { it.kind to it.text })
    }

    @Test
    fun `a genuine bank OTP notification is still not a request, in either language`() {
        listOf(
            "123456 is your OTP for HDFC Bank. Do not share it with anyone.",
            "आपका ओटीपी 123456 है। इसे किसी को न बताएं।",
            "Aapka OTP 123456 hai. Kisi ko na bataiye.",
        ).forEach { text ->
            assertThat(wrapped.extract(text).otpRequest).isFalse()
        }
    }

    @Test
    fun `TrustLens own advice is not scored as an attack`() {
        // The app's own warning wording contains every dangerous noun there is.
        listOf(
            "Do not share your OTP or PIN. Do not pay.",
            "अपना ओटीपी या पिन किसी को न बताएं। पैसे न भेजें।",
        ).forEach { text ->
            val indicators = wrapped.extract(text)
            assertThat(indicators.otpRequest).isFalse()
            assertThat(indicators.credentialRequest).isFalse()
            assertThat(indicators.paymentRequest).isFalse()
        }
    }

    @Test
    fun `the wrapper never clears a flag the frozen extractor set`() {
        val corpus = englishCorpus + listOf(
            "मैं एसबीआई से बोल रहा हूँ, अभी OTP बताइए।",
            "Sir aapka KYC expire ho gaya hai, abhi OTP bataiye.",
        )

        corpus.forEach { text ->
            val before = frozen.extract(text)
            val after = wrapped.extract(text)

            // Monotonic by construction; asserted because "by construction" is how
            // invariants quietly stop being true.
            listOf(
                before.otpRequest to after.otpRequest,
                before.credentialRequest to after.credentialRequest,
                before.paymentRequest to after.paymentRequest,
                before.urgency to after.urgency,
                before.accountThreat to after.accountThreat,
            ).forEach { (wasSet, isSet) ->
                if (wasSet) assertThat(isSet).isTrue()
            }
            assertThat(after.organisations).containsAtLeastElementsIn(before.organisations)
        }
    }

    private fun Indicators.flags() = listOf(
        otpRequest,
        credentialRequest,
        paymentRequest,
        urgency,
        accountThreat,
    )
}
