package com.example.guardband.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AlertParser.parseHistory]: ordering, and the skip-and-count rule for
 * malformed entries.
 *
 * This is the logic [FirebaseAlertRepository] runs on every snapshot, lifted
 * out of the Firebase class precisely so it can be tested without one.
 */
class AlertHistoryParsingTest {

    private fun entry(sequenceId: Any?, type: String = "PANIC"): Map<String, Any?> = mapOf(
        "schemaVersion" to "1.0",
        "deviceId" to "guardband-001",
        "type" to type,
        "timestamp" to "2026-10-04T08:45:00Z",
        "battery" to mapOf("percent" to 64L, "isCharging" to false),
        "sequenceId" to sequenceId
    )

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    fun `history comes back newest first`() {
        val history = AlertParser.parseHistory(
            listOf(entry(1L), entry(2L), entry(3L), entry(4L))
        )

        assertEquals(listOf(4L, 3L, 2L, 1L), history.alerts.map { it.sequenceId })
    }

    @Test
    fun `an out-of-order window is still sorted newest first`() {
        // RTDB returns string order for keys that are not integer-like; the
        // parser must not depend on the query's ordering.
        val history = AlertParser.parseHistory(
            listOf(entry(10L), entry(2L), entry(33L), entry(4L))
        )

        assertEquals(listOf(33L, 10L, 4L, 2L), history.alerts.map { it.sequenceId })
    }

    @Test
    fun `an empty node parses to an empty history`() {
        val history = AlertParser.parseHistory(emptyList())

        assertTrue(history.alerts.isEmpty())
        assertEquals(0, history.rawCount)
        assertEquals(0, history.malformedCount)
    }

    // ── Malformed entries are skipped and counted ─────────────────────────────

    @Test
    fun `a malformed entry is skipped without losing the good ones`() {
        val history = AlertParser.parseHistory(
            listOf(entry(1L), entry(sequenceId = null), entry(3L))
        )

        assertEquals(listOf(3L, 1L), history.alerts.map { it.sequenceId })
        assertEquals(3, history.rawCount)
        assertEquals(1, history.malformedCount)
    }

    @Test
    fun `several malformed entries are all counted`() {
        val history = AlertParser.parseHistory(
            listOf(entry(1L), "not a map", null, entry(sequenceId = "nope"), entry(5L))
        )

        assertEquals(listOf(5L, 1L), history.alerts.map { it.sequenceId })
        assertEquals(5, history.rawCount)
        assertEquals(3, history.malformedCount)
    }

    @Test
    fun `a window of nothing but malformed entries reports them rather than failing`() {
        val history = AlertParser.parseHistory(listOf("junk", 42L, null))

        assertTrue(history.alerts.isEmpty())
        assertEquals(3, history.rawCount)
        assertEquals(3, history.malformedCount)
        // The caller can tell this apart from "the band has sent nothing",
        // which is the whole point of carrying the counts.
    }

    @Test
    fun `an unknown type counts as readable, not malformed`() {
        val history = AlertParser.parseHistory(listOf(entry(1L, type = "FALL_DETECTED")))

        assertEquals(1, history.alerts.size)
        assertEquals(0, history.malformedCount)
        assertEquals("FALL_DETECTED", history.alerts.single().type)
    }

    @Test
    fun `rawCount counts entries read, not alerts shown`() {
        val history = AlertParser.parseHistory(
            listOf(entry(1L, "TRACKING_UPDATE"), entry(2L, "PANIC"), entry(sequenceId = null))
        )

        // Tracking updates are still present here: D1 filtering is the
        // ViewModel's job, not the parser's.
        assertEquals(3, history.rawCount)
        assertEquals(2, history.alerts.size)
        assertEquals(1, history.malformedCount)
    }
}
