package com.trustlens.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for duplicate suppression in the continuous QR scanner. */
class ScanDebouncerTest {

    private val debouncer = ScanDebouncer(windowMillis = 3_000)

    @Test
    fun `the first sighting of a payload is processed`() {
        assertThat(debouncer.shouldProcess("https://example.com", nowMillis = 0)).isTrue()
    }

    @Test
    fun `the same payload seen again immediately is suppressed`() {
        debouncer.shouldProcess("https://example.com", nowMillis = 0)

        assertThat(debouncer.shouldProcess("https://example.com", nowMillis = 30)).isFalse()
    }

    @Test
    fun `a code held steadily in frame is analysed exactly once`() {
        val accepted = (0..120).count { frame ->
            // 120 frames over four seconds, longer than the window.
            debouncer.shouldProcess("https://example.com", nowMillis = frame * 33L)
        }

        assertThat(accepted).isEqualTo(1)
    }

    @Test
    fun `a different payload is processed even within the window`() {
        debouncer.shouldProcess("https://example.com", nowMillis = 0)

        assertThat(debouncer.shouldProcess("upi://pay?pa=a@b", nowMillis = 100)).isTrue()
    }

    @Test
    fun `the same payload is processed again after the window has elapsed`() {
        debouncer.shouldProcess("https://example.com", nowMillis = 0)

        assertThat(debouncer.shouldProcess("https://example.com", nowMillis = 5_000)).isTrue()
    }

    @Test
    fun `a reset allows the user to deliberately rescan the same code`() {
        debouncer.shouldProcess("https://example.com", nowMillis = 0)
        debouncer.reset()

        assertThat(debouncer.shouldProcess("https://example.com", nowMillis = 10)).isTrue()
    }

    @Test
    fun `a blank payload is never processed`() {
        assertThat(debouncer.shouldProcess("", nowMillis = 0)).isFalse()
        assertThat(debouncer.shouldProcess("   ", nowMillis = 1_000)).isFalse()
    }

    @Test
    fun `alternating payloads are each processed`() {
        val payloads = listOf("a", "b", "a", "b")

        val accepted = payloads.mapIndexed { index, payload ->
            debouncer.shouldProcess(payload, nowMillis = index * 10L)
        }

        assertThat(accepted).containsExactly(true, true, true, true).inOrder()
    }
}
