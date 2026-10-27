package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BandReport], the band-status rules Track and the Device tab share:
 * reporting or last seen, never reported, the 180 s threshold, the low
 * battery level, and what is retained across reports. Pure, so no
 * Robolectric.
 */
class BandReportTest {

    /** 2026-10-27T12:00:00Z. */
    private val now = BandStatus.parseIsoUtcMillis("2026-10-27T12:00:00Z")!!

    private fun report(
        timestamp: String,
        type: String = "CHECKIN",
        location: Alert.Location? = Alert.Location(10.3157, 123.8854, 8.5),
        battery: Alert.Battery? = Alert.Battery(percent = 64, isCharging = false)
    ) = Alert(
        schemaVersion = "1.0",
        deviceId = "guardband-001",
        type = type,
        timestamp = timestamp,
        location = location,
        battery = battery,
        sequenceId = 1
    )

    private fun battery(percent: Int) =
        BandReport.NONE.after(report("2026-10-27T11:59:00Z", battery = Alert.Battery(percent, false)))

    // ── Reporting and last seen ───────────────────────────────────────────────

    @Test
    fun `a report a minute ago is reporting`() {
        val band = BandReport.NONE.after(report("2026-10-27T11:59:00Z"))

        assertTrue(band.hasReported)
        assertTrue(band.isReporting(now))
        assertEquals(Elapsed.Minutes(1), band.elapsed(now))
    }

    @Test
    fun `a report five minutes ago is last seen five minutes ago, not reporting`() {
        val band = BandReport.NONE.after(report("2026-10-27T11:55:00Z"))

        assertTrue(band.hasReported)
        assertFalse(band.isReporting(now))
        assertEquals(Elapsed.Minutes(5), band.elapsed(now))
    }

    @Test
    fun `the same report ages as the clock moves on`() {
        val band = BandReport.NONE.after(report("2026-10-27T11:59:00Z"))

        assertTrue(band.isReporting(now))
        assertFalse(band.isReporting(now + 10 * 60_000L))
        assertEquals(Elapsed.Minutes(11), band.elapsed(now + 10 * 60_000L))
    }

    @Test
    fun `exactly at the 180 s threshold the band is no longer reporting`() {
        val band = BandReport.NONE.after(report("2026-10-27T11:57:00Z"))

        assertEquals(BandStatus.ONLINE_THRESHOLD_MS, now - band.reportedAtMillis!!)
        assertFalse(band.isReporting(now))
        assertEquals(Elapsed.Minutes(3), band.elapsed(now))
    }

    @Test
    fun `one millisecond inside the threshold is still reporting`() {
        val band = BandReport.NONE.after(report("2026-10-27T11:57:00Z"))

        assertTrue(band.isReporting(now - 1))
    }

    @Test
    fun `a band clock running ahead of the phone counts as reporting`() {
        val band = BandReport.NONE.after(report("2026-10-27T12:01:00Z"))

        assertTrue(band.isReporting(now))
        assertEquals(Elapsed.Seconds(0), band.elapsed(now))
    }

    // ── Never reported ────────────────────────────────────────────────────────

    @Test
    fun `a band that never reported has nothing to say`() {
        val band = BandReport.NONE

        assertFalse(band.hasReported)
        assertFalse(band.isReporting(now))
        assertNull(band.elapsed(now))
        assertNull(band.location)
        assertNull(band.battery)
        assertFalse(band.isBatteryLow)
    }

    @Test
    fun `a null report clears everything a previous one said`() {
        val band = BandReport.NONE.after(report("2026-10-27T11:59:00Z")).after(null)

        assertEquals(BandReport.NONE, band)
    }

    @Test
    fun `an unreadable timestamp is a report with no freshness, not a fresh one`() {
        val band = BandReport.NONE.after(report("not a time"))

        assertTrue(band.hasReported)
        assertFalse(band.isReporting(now))
        assertNull(band.elapsed(now))
    }

    // ── Battery ───────────────────────────────────────────────────────────────

    @Test
    fun `battery at 20 percent is low`() {
        assertTrue(battery(20).isBatteryLow)
    }

    @Test
    fun `battery at 19 percent is low`() {
        assertTrue(battery(19).isBatteryLow)
    }

    @Test
    fun `battery at 21 percent is not low`() {
        assertFalse(battery(21).isBatteryLow)
    }

    // ── What is retained ──────────────────────────────────────────────────────

    @Test
    fun `a report without a fix or a battery keeps the last known ones`() {
        val first = report("2026-10-27T11:58:00Z")
        val band = BandReport.NONE
            .after(first)
            .after(report("2026-10-27T11:59:00Z", type = "TRACKING_UPDATE", location = null, battery = null))

        assertEquals(first.location, band.location)
        assertEquals(first.battery, band.battery)
        // The newer report still counts as the last time it was heard from.
        assertEquals(Elapsed.Minutes(1), band.elapsed(now))
    }

    @Test
    fun `a newer fix replaces the position`() {
        val moved = Alert.Location(10.3200, 123.8900, 5.0)
        val band = BandReport.NONE
            .after(report("2026-10-27T11:58:00Z"))
            .after(report("2026-10-27T11:59:00Z", location = moved))

        assertEquals(moved, band.location)
    }

    @Test
    fun `a tracking update's fix is marked as tracking, an alert's is not`() {
        val tracking = BandReport.NONE.after(report("2026-10-27T11:59:00Z", type = "TRACKING_UPDATE"))
        val alert = tracking.after(report("2026-10-27T11:59:30Z", type = "PANIC"))

        assertTrue(tracking.locationFromTracking)
        assertFalse(alert.locationFromTracking)
    }

    @Test
    fun `a report without a fix keeps where the retained fix came from`() {
        val band = BandReport.NONE
            .after(report("2026-10-27T11:58:00Z", type = "PANIC"))
            .after(report("2026-10-27T11:59:00Z", type = "TRACKING_UPDATE", location = null))

        assertFalse(band.locationFromTracking)
    }
}
