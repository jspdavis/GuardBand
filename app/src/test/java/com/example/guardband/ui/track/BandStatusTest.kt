package com.example.guardband.ui.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BandStatus] is pure, so these run without Robolectric and without a
 * database: no `android.*` is reached, and the clock is a parameter.
 */
class BandStatusTest {

    /** An arbitrary fixed "now"; only the differences matter. */
    private val now = 1_760_000_000_000L

    private fun secondsAgo(seconds: Long) = now - seconds * 1000L

    // ── Online threshold ──────────────────────────────────────────────────────

    @Test
    fun `a report just now is online`() {
        assertTrue(BandStatus.isOnline(now, now))
    }

    @Test
    fun `a report one second inside the threshold is online`() {
        val reported = now - BandStatus.ONLINE_THRESHOLD_MS + 1000L

        assertTrue(BandStatus.isOnline(reported, now))
    }

    @Test
    fun `a report exactly at the threshold is offline`() {
        val reported = now - BandStatus.ONLINE_THRESHOLD_MS

        assertFalse(BandStatus.isOnline(reported, now))
    }

    @Test
    fun `a report past the threshold is offline`() {
        assertFalse(BandStatus.isOnline(secondsAgo(600), now))
    }

    @Test
    fun `the threshold is three of FR-09's sixty second intervals`() {
        assertEquals(180_000L, BandStatus.ONLINE_THRESHOLD_MS)
    }

    @Test
    fun `a timestamp from the future counts as online rather than offline`() {
        // The band's clock can run ahead of the phone's; "just reported" is a
        // better answer than "offline".
        assertTrue(BandStatus.isOnline(now + 60_000L, now))
    }

    @Test
    fun `the threshold can be overridden`() {
        val reported = secondsAgo(10)

        assertTrue(BandStatus.isOnline(reported, now, thresholdMs = 20_000L))
        assertFalse(BandStatus.isOnline(reported, now, thresholdMs = 5_000L))
    }

    // ── Elapsed, at every boundary ────────────────────────────────────────────

    @Test
    fun `under a minute reads in seconds`() {
        assertEquals(Elapsed.Seconds(0), BandStatus.elapsedSince(now, now))
        assertEquals(Elapsed.Seconds(1), BandStatus.elapsedSince(secondsAgo(1), now))
        assertEquals(Elapsed.Seconds(59), BandStatus.elapsedSince(secondsAgo(59), now))
    }

    @Test
    fun `a minute flips to minutes`() {
        assertEquals(Elapsed.Minutes(1), BandStatus.elapsedSince(secondsAgo(60), now))
    }

    @Test
    fun `under an hour reads in minutes`() {
        assertEquals(Elapsed.Minutes(59), BandStatus.elapsedSince(secondsAgo(3599), now))
    }

    @Test
    fun `an hour flips to hours`() {
        assertEquals(Elapsed.Hours(1), BandStatus.elapsedSince(secondsAgo(3600), now))
    }

    @Test
    fun `under a day reads in hours`() {
        assertEquals(Elapsed.Hours(23), BandStatus.elapsedSince(secondsAgo(86_399), now))
    }

    @Test
    fun `a day flips to days`() {
        assertEquals(Elapsed.Days(1), BandStatus.elapsedSince(secondsAgo(86_400), now))
    }

    @Test
    fun `more than a day reads in days`() {
        assertEquals(Elapsed.Days(9), BandStatus.elapsedSince(secondsAgo(9 * 86_400 + 500), now))
    }

    @Test
    fun `a future timestamp clamps to zero rather than going negative`() {
        assertEquals(Elapsed.Seconds(0), BandStatus.elapsedSince(now + 60_000L, now))
    }

    // ── Timestamp parsing ─────────────────────────────────────────────────────

    @Test
    fun `it parses SCHEMA-md's format, without milliseconds`() {
        assertEquals(0L, BandStatus.parseIsoUtcMillis("1970-01-01T00:00:00Z"))
    }

    @Test
    fun `it parses the mock sender's format, with milliseconds`() {
        assertEquals(123L, BandStatus.parseIsoUtcMillis("1970-01-01T00:00:00.123Z"))
    }

    @Test
    fun `it reads the timestamp as UTC, not the default zone`() {
        // 1760000000000 epoch millis is 2025-10-09T08:53:20Z.
        assertEquals(1_760_000_000_000L, BandStatus.parseIsoUtcMillis("2025-10-09T08:53:20Z"))
    }

    @Test
    fun `an unreadable timestamp is null rather than an exception or a zero`() {
        assertNull(BandStatus.parseIsoUtcMillis(""))
        assertNull(BandStatus.parseIsoUtcMillis("not a timestamp"))
        assertNull(BandStatus.parseIsoUtcMillis("2026-10-04 08:45:00"))
    }

    @Test
    fun `an out of range date is rejected rather than rolled over`() {
        // isLenient = false, so month 13 is not quietly January of next year.
        assertNull(BandStatus.parseIsoUtcMillis("2026-13-04T08:45:00Z"))
    }
}
