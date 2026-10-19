package com.example.guardband.ui.notifications

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.data.repository.AlertError
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.ui.notifications.AlertNotificationDecider.MISSED_ALERT_WINDOW_MS
import com.example.guardband.ui.track.BandStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which alerts in a history emission become notifications, and the mark
 * stored afterwards. Every rule in [AlertNotificationDecider]'s KDoc.
 */
class AlertNotificationDeciderTest {

    private val now = BandStatus.parseIsoUtcMillis("2026-10-19T12:00:00Z")!!

    private fun alert(
        seq: Long,
        type: String = AlertType.PANIC.name,
        timestamp: String = "2026-10-19T11:59:00Z"
    ) = Alert(schemaVersion = "1.0", deviceId = "guardband-001", type = type, timestamp = timestamp, sequenceId = seq)

    private fun history(vararg alerts: Alert) =
        Result.success(AlertHistory(alerts.sortedByDescending { it.sequenceId }, alerts.size, 0))

    private fun decide(mark: Long?, emission: Result<AlertHistory>, first: Boolean = false) =
        AlertNotificationDecider.decide(mark, emission, first, now)

    private fun AlertNotificationPlan.posted() = toPost.map { it.sequenceId }

    // ── Baseline ──────────────────────────────────────────────────────────────

    @Test
    fun `with no mark the newest is stored and nothing is posted`() {
        val plan = decide(null, history(alert(1), alert(2), alert(3)))

        assertTrue(plan.toPost.isEmpty())
        assertEquals(0, plan.summaryCount)
        assertEquals(3L, plan.newMark)
    }

    @Test
    fun `an empty history stores 0, so the band's first alert is still posted`() {
        val baseline = decide(null, history())
        assertEquals(0L, baseline.newMark)

        val next = decide(baseline.newMark, history(alert(1)))
        assertEquals(listOf(1L), next.posted())
    }

    // ── New entries ───────────────────────────────────────────────────────────

    @Test
    fun `only entries above the mark are posted`() {
        val plan = decide(2, history(alert(1), alert(2), alert(3)))

        assertEquals(listOf(3L), plan.posted())
        assertEquals(3L, plan.newMark)
    }

    @Test
    fun `panic, low battery, check-in and an unknown type are all posted`() {
        val plan = decide(
            0,
            history(
                alert(1, AlertType.PANIC.name),
                alert(2, AlertType.LOW_BATTERY.name),
                alert(3, AlertType.CHECKIN.name)
            )
        )
        assertEquals(listOf(3L, 2L, 1L), plan.posted())

        assertEquals(listOf(4L), decide(3, history(alert(4, "FALL_DETECTED"))).posted())
    }

    @Test
    fun `a tracking update is never posted but still advances the mark`() {
        val plan = decide(5, history(alert(6, AlertType.TRACKING_UPDATE.name), alert(7, AlertType.TRACKING_UPDATE.name)))

        assertTrue(plan.toPost.isEmpty())
        assertEquals(0, plan.summaryCount)
        assertEquals(7L, plan.newMark)
    }

    @Test
    fun `the same alert is not posted twice across emissions`() {
        val first = decide(0, history(alert(1)))
        val second = decide(first.newMark, history(alert(1)))

        assertEquals(listOf(1L), first.posted())
        assertTrue(second.toPost.isEmpty())
        assertEquals(1L, second.newMark)
    }

    // ── First emission after a (re)start ──────────────────────────────────────

    @Test
    fun `after a restart a missed alert inside the window is posted`() {
        val justInside = alert(2, timestamp = "2026-10-19T11:30:00Z") // exactly 30 min
        assertEquals(listOf(2L), decide(1, history(justInside), first = true).posted())
    }

    @Test
    fun `after a restart a missed alert outside the window is absorbed`() {
        val justOutside = alert(2, timestamp = "2026-10-19T11:29:59Z")
        val plan = decide(1, history(justOutside), first = true)

        assertTrue(plan.toPost.isEmpty())
        assertEquals(2L, plan.newMark)
    }

    @Test
    fun `after a restart an unreadable timestamp is absorbed`() {
        val plan = decide(1, history(alert(2, timestamp = "not a time")), first = true)

        assertTrue(plan.toPost.isEmpty())
        assertEquals(2L, plan.newMark)
    }

    @Test
    fun `after a restart a timestamp ahead of the phone's clock counts as recent`() {
        assertEquals(listOf(2L), decide(1, history(alert(2, timestamp = "2026-10-19T12:05:00Z")), first = true).posted())
    }

    @Test
    fun `later emissions post old timestamps too`() {
        val old = alert(2, timestamp = "2026-10-18T08:00:00Z")
        assertEquals(listOf(2L), decide(1, history(old), first = false).posted())
    }

    @Test
    fun `the window is FR-09's 30 minutes`() {
        assertEquals(30 * 60 * 1000L, MISSED_ALERT_WINDOW_MS)
    }

    // ── History reset ─────────────────────────────────────────────────────────

    @Test
    fun `a history reset re-baselines and posts nothing`() {
        val plan = decide(40, history(alert(1), alert(2)))

        assertTrue(plan.toPost.isEmpty())
        assertEquals(2L, plan.newMark)
    }

    @Test
    fun `a cleared history re-baselines to 0`() {
        assertEquals(0L, decide(40, history()).newMark)
    }

    // ── Cap ───────────────────────────────────────────────────────────────────

    @Test
    fun `at most three are posted, newest first, and the rest are one summary`() {
        val plan = decide(0, history(alert(1), alert(2), alert(3), alert(4), alert(5)))

        assertEquals(listOf(5L, 4L, 3L), plan.posted())
        assertEquals(2, plan.summaryCount)
        assertEquals(5L, plan.newMark)
    }

    @Test
    fun `exactly three need no summary`() {
        assertEquals(0, decide(0, history(alert(1), alert(2), alert(3))).summaryCount)
    }

    @Test
    fun `tracking updates do not count towards the cap`() {
        val plan = decide(
            0,
            history(
                alert(1), alert(2),
                alert(3, AlertType.TRACKING_UPDATE.name),
                alert(4, AlertType.TRACKING_UPDATE.name),
                alert(5)
            )
        )
        assertEquals(listOf(5L, 2L, 1L), plan.posted())
        assertEquals(0, plan.summaryCount)
    }

    // ── Failure ───────────────────────────────────────────────────────────────

    @Test
    fun `a failed read changes nothing`() {
        val plan = decide(7, Result.failure(AlertError.Network))

        assertTrue(plan.toPost.isEmpty())
        assertEquals(0, plan.summaryCount)
        assertEquals(7L, plan.newMark)
    }

    @Test
    fun `a failed read with no mark leaves it unset`() {
        assertEquals(null, decide(null, Result.failure(AlertError.Network)).newMark)
    }
}
