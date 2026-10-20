package com.example.guardband.ui.alert

import com.example.guardband.testing.FakeAlertRepository.Companion.alert
import com.example.guardband.ui.track.BandStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Alert history's day headings: which day an alert falls on in the phone's
 * zone, and where the headings go. Manila (UTC+8, no daylight saving), so a
 * UTC evening is the next morning there.
 */
class AlertDaysTest {

    private val manila = TimeZone.getTimeZone("Asia/Manila")

    /** Noon on Tue 20 Oct 2026 in Manila. */
    private val now = millis("2026-10-20T04:00:00Z")

    private fun millis(iso: String) = checkNotNull(BandStatus.parseIsoUtcMillis(iso))

    private fun dayOf(iso: String) = AlertDays.dayOf(iso, now, manila)

    private fun row(sequenceId: Long, timestamp: String) =
        AlertRow(alert(sequenceId, "PANIC", timestamp = timestamp))

    @Test
    fun `this morning is today`() {
        assertEquals(AlertDay.Today, dayOf("2026-10-20T00:30:00Z"))
    }

    @Test
    fun `the day is the phone's, not UTC's`() {
        // 23:59 and 00:00 in Manila, both on 19 Oct in UTC.
        assertEquals(AlertDay.Yesterday, dayOf("2026-10-19T15:59:00Z"))
        assertEquals(AlertDay.Today, dayOf("2026-10-19T16:00:00Z"))
    }

    @Test
    fun `an earlier day this year carries its midnight and no year`() {
        assertEquals(AlertDay.On(millis("2026-10-17T16:00:00Z"), sameYear = true), dayOf("2026-10-18T10:00:00Z"))
    }

    @Test
    fun `a day in another year says so`() {
        val day = dayOf("2025-12-31T10:00:00Z") as AlertDay.On

        assertEquals(false, day.sameYear)
    }

    @Test
    fun `a timestamp ahead of the phone's clock counts as today`() {
        assertEquals(AlertDay.Today, dayOf("2026-10-21T01:00:00Z"))
    }

    @Test
    fun `an unreadable timestamp is an unknown day`() {
        assertEquals(AlertDay.Unknown, dayOf("yesterday-ish"))
    }

    @Test
    fun `a heading goes above each day's first row, and only there`() {
        val today1 = row(4, "2026-10-20T02:00:00Z")
        val today2 = row(3, "2026-10-20T01:00:00Z")
        val yesterday = row(2, "2026-10-19T10:00:00Z")
        val earlier = row(1, "2026-10-18T10:00:00Z")

        val items = AlertDays.group(listOf(today1, today2, yesterday, earlier), now, manila)

        assertEquals(
            listOf(
                AlertListItem.Header(AlertDay.Today),
                AlertListItem.Row(today1),
                AlertListItem.Row(today2),
                AlertListItem.Header(AlertDay.Yesterday),
                AlertListItem.Row(yesterday),
                AlertListItem.Header(AlertDay.On(millis("2026-10-17T16:00:00Z"), sameYear = true)),
                AlertListItem.Row(earlier)
            ),
            items
        )
    }

    @Test
    fun `headings follow the list's order, so a clock that jumped shows a day twice`() {
        // Ordered by sequenceId: the band's clock went back a day between 2 and 3.
        val items = AlertDays.group(
            listOf(row(3, "2026-10-19T10:00:00Z"), row(2, "2026-10-20T02:00:00Z"), row(1, "2026-10-19T09:00:00Z")),
            now,
            manila
        )

        assertEquals(3, items.count { it is AlertListItem.Header })
        assertEquals(6, items.size)
    }

    @Test
    fun `no rows, no headings`() {
        assertTrue(AlertDays.group(emptyList(), now, manila).isEmpty())
    }
}
