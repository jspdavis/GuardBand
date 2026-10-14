package com.example.guardband.data.repository

import com.example.guardband.data.model.CheckIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The latest check-in, from raw wire maps to [CheckIn].
 *
 * Runs the whole read path the repository runs on each snapshot:
 * [AlertParser.parseHistory] over the window, then [CheckInSelector]. So the
 * "missing fields" and "wrong types" cases below exercise the one parser that
 * reads this payload, not a second copy of its rules.
 */
class CheckInSelectorTest {

    private fun entry(
        sequenceId: Any? = 1L,
        type: Any? = "CHECKIN",
        timestamp: Any? = "2026-10-14T08:45:00Z"
    ): Map<String, Any?> = mapOf(
        "schemaVersion" to "1.0",
        "deviceId" to "guardband-001",
        "type" to type,
        "timestamp" to timestamp,
        "location" to mapOf("lat" to 10.3157, "lng" to 123.8854, "accuracyMeters" to 8.5),
        "battery" to mapOf("percent" to 64L, "isCharging" to false),
        "sequenceId" to sequenceId
    )

    private fun latest(vararg raw: Any?): CheckIn? =
        CheckInSelector.latestFrom(AlertParser.parseHistory(raw.toList()))

    // ── A valid payload ───────────────────────────────────────────────────────

    @Test
    fun `a valid check-in is read with its sequence id and timestamp`() {
        assertEquals(
            CheckIn(sequenceId = 7L, timestamp = "2026-10-14T08:45:00Z"),
            latest(entry(sequenceId = 7L))
        )
    }

    @Test
    fun `a check-in without location or battery still counts`() {
        val bare = mapOf("type" to "CHECKIN", "timestamp" to "2026-10-14T08:45:00Z", "sequenceId" to 3L)

        assertEquals(CheckIn(3L, "2026-10-14T08:45:00Z"), latest(bare))
    }

    // ── Missing fields ────────────────────────────────────────────────────────

    @Test
    fun `a check-in with no timestamp is not a check-in`() {
        assertNull(latest(entry(timestamp = null)))
    }

    @Test
    fun `a check-in with no sequence id is not a check-in`() {
        assertNull(latest(entry(sequenceId = null)))
    }

    @Test
    fun `an entry with no type is not a check-in`() {
        assertNull(latest(entry(type = null)))
    }

    // ── Wrong types ───────────────────────────────────────────────────────────

    @Test
    fun `a sequence id written as a string is rejected`() {
        assertNull(latest(entry(sequenceId = "7")))
    }

    @Test
    fun `a timestamp written as a number is rejected`() {
        assertNull(latest(entry(timestamp = 1_760_000_000L)))
    }

    @Test
    fun `a node that is not a map is rejected`() {
        assertNull(latest("CHECKIN", 42L, null))
    }

    @Test
    fun `only the exact CHECKIN type counts`() {
        assertNull(latest(entry(type = "checkin")))
        assertNull(latest(entry(type = "CHECK_IN")))
    }

    @Test
    fun `a malformed newer check-in does not hide an older good one`() {
        assertEquals(
            CheckIn(4L, "2026-10-14T08:45:00Z"),
            latest(entry(sequenceId = 4L), entry(sequenceId = 9L, timestamp = 123L))
        )
    }

    // ── Empty and non-check-in windows ────────────────────────────────────────

    @Test
    fun `an empty history has no check-in`() {
        assertNull(latest())
    }

    @Test
    fun `a window of other alert types has no check-in`() {
        assertNull(
            latest(
                entry(sequenceId = 1L, type = "PANIC"),
                entry(sequenceId = 2L, type = "TRACKING_UPDATE"),
                entry(sequenceId = 3L, type = "LOW_BATTERY")
            )
        )
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    fun `of two check-ins the higher sequence id wins`() {
        assertEquals(
            CheckIn(8L, "2026-10-14T09:00:00Z"),
            latest(
                entry(sequenceId = 5L, timestamp = "2026-10-14T08:00:00Z"),
                entry(sequenceId = 8L, timestamp = "2026-10-14T09:00:00Z")
            )
        )
    }

    @Test
    fun `the higher sequence id wins even when it arrives first`() {
        assertEquals(
            CheckIn(8L, "2026-10-14T09:00:00Z"),
            latest(
                entry(sequenceId = 8L, timestamp = "2026-10-14T09:00:00Z"),
                entry(sequenceId = 5L, timestamp = "2026-10-14T08:00:00Z")
            )
        )
    }

    @Test
    fun `later tracking updates do not displace the latest check-in`() {
        assertEquals(
            CheckIn(2L, "2026-10-14T08:45:00Z"),
            latest(
                entry(sequenceId = 2L),
                entry(sequenceId = 3L, type = "TRACKING_UPDATE"),
                entry(sequenceId = 4L, type = "TRACKING_UPDATE")
            )
        )
    }
}
