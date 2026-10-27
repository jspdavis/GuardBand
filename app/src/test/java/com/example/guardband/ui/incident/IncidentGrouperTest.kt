package com.example.guardband.ui.incident

import com.example.guardband.data.model.Alert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which tracking updates belong to a panic. SCHEMA.md has no incident id, so
 * it is by `sequenceId` (after the panic, before the next one) and time (30
 * minutes, FR-09). Pure: no Android, no Robolectric.
 */
class IncidentGrouperTest {

    /** 2025-10-09T08:53:20Z. */
    private val t0 = 1_760_000_000_000L
    private val minute = 60_000L

    private fun isoAt(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private fun spot(index: Int) = Alert.Location(10.3157 + index * 0.0001, 123.8854, 8.5)

    private fun alert(
        sequenceId: Long,
        type: String,
        millis: Long = t0 + sequenceId * minute,
        location: Alert.Location? = spot(sequenceId.toInt()),
        battery: Alert.Battery? = Alert.Battery(80 - sequenceId.toInt(), false)
    ) = Alert(
        schemaVersion = "1.0",
        deviceId = "guardband-001",
        type = type,
        timestamp = isoAt(millis),
        location = location,
        battery = battery,
        sequenceId = sequenceId
    )

    private fun tracking(sequenceId: Long, millis: Long = t0 + sequenceId * minute) =
        alert(sequenceId, "TRACKING_UPDATE", millis)

    private fun updateIds(incident: Incident?) = incident!!.trackingUpdates.map { it.sequenceId }

    @Test
    fun `a panic with no updates is an incident with none`() {
        val incident = IncidentGrouper.incidentFor(10, listOf(alert(10, "PANIC")))

        assertEquals(10L, incident!!.panic.sequenceId)
        assertEquals(emptyList<Alert>(), incident.trackingUpdates)
        assertNull(incident.latestUpdate)
        assertEquals(listOf(spot(10)), incident.path)
    }

    @Test
    fun `the updates after a panic are its own, oldest first`() {
        val alerts = listOf(alert(10, "PANIC"), tracking(11), tracking(12), tracking(13))

        val incident = IncidentGrouper.incidentFor(10, alerts)

        assertEquals(listOf(11L, 12L, 13L), updateIds(incident))
        assertEquals(13L, incident!!.latestUpdate?.sequenceId)
        assertEquals(listOf(spot(10), spot(11), spot(12), spot(13)), incident.path)
    }

    @Test
    fun `updates more than 30 minutes after the panic are left out`() {
        val alerts = listOf(
            alert(10, "PANIC", millis = t0),
            tracking(11, t0 + 29 * minute),
            tracking(12, t0 + 30 * minute),
            tracking(13, t0 + 31 * minute),
            tracking(14, t0 + 90 * minute)
        )

        assertEquals(listOf(11L, 12L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
    }

    @Test
    fun `two panics back to back each keep their own updates`() {
        val alerts = listOf(
            alert(10, "PANIC"), tracking(11), tracking(12),
            alert(13, "PANIC"), tracking(14), tracking(15)
        )

        assertEquals(listOf(11L, 12L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
        assertEquals(listOf(14L, 15L), updateIds(IncidentGrouper.incidentFor(13, alerts)))
    }

    @Test
    fun `a panic straight after another leaves the first with none`() {
        val alerts = listOf(alert(10, "PANIC"), alert(11, "PANIC"), tracking(12))

        assertEquals(emptyList<Long>(), updateIds(IncidentGrouper.incidentFor(10, alerts)))
        assertEquals(listOf(12L), updateIds(IncidentGrouper.incidentFor(11, alerts)))
    }

    @Test
    fun `order follows sequenceId, whatever the timestamps say`() {
        // The band's clock jumped back after the panic.
        val alerts = listOf(
            alert(10, "PANIC", millis = t0 + 10 * minute),
            tracking(11, t0 + 12 * minute),
            tracking(12, t0 + 5 * minute),
            tracking(13, t0 + 11 * minute)
        )

        assertEquals(listOf(11L, 12L, 13L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
    }

    @Test
    fun `an update stamped before its panic still belongs to it`() {
        val alerts = listOf(alert(10, "PANIC", millis = t0), tracking(11, t0 - 2 * minute))

        assertEquals(listOf(11L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
    }

    @Test
    fun `the input order does not matter`() {
        val alerts = listOf(tracking(13), alert(10, "PANIC"), tracking(11), tracking(12), tracking(11))

        assertEquals(listOf(11L, 12L, 13L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
    }

    @Test
    fun `a tracking update with no earlier panic belongs to no incident`() {
        val alerts = listOf(tracking(8), tracking(9), alert(10, "PANIC"), tracking(11))

        assertEquals(listOf(11L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
        assertNull(IncidentGrouper.incidentFor(8, alerts))
    }

    @Test
    fun `only a panic starts an incident`() {
        val alerts = listOf(alert(10, "CHECKIN"), tracking(11))

        assertNull(IncidentGrouper.incidentFor(10, alerts))
        assertNull(IncidentGrouper.incidentFor(99, alerts))
    }

    @Test
    fun `a check-in or low-battery alert in between does not end the incident`() {
        val alerts = listOf(
            alert(10, "PANIC"), tracking(11), alert(12, "CHECKIN"),
            alert(13, "LOW_BATTERY"), tracking(14)
        )

        assertEquals(listOf(11L, 14L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
    }

    @Test
    fun `an unreadable timestamp is grouped by order alone`() {
        val alerts = listOf(alert(10, "PANIC"), tracking(11).copy(timestamp = "soon"))

        assertEquals(listOf(11L), updateIds(IncidentGrouper.incidentFor(10, alerts)))
        assertEquals(
            listOf(11L),
            updateIds(IncidentGrouper.incidentFor(10, listOf(alert(10, "PANIC").copy(timestamp = "?"), tracking(11))))
        )
    }

    @Test
    fun `updates with no position count but are not on the path`() {
        val alerts = listOf(
            alert(10, "PANIC"),
            tracking(11),
            alert(12, "TRACKING_UPDATE", location = null),
            alert(13, "TRACKING_UPDATE", location = Alert.Location(0.0, 0.0, 0.0))
        )

        val incident = IncidentGrouper.incidentFor(10, alerts)!!

        assertEquals(listOf(11L, 12L, 13L), incident.trackingUpdates.map { it.sequenceId })
        assertEquals(listOf(spot(11)), incident.trackedLocations)
        assertEquals(listOf(spot(10), spot(11)), incident.path)
    }

    @Test
    fun `the battery is the newest reading in the incident`() {
        val alerts = listOf(
            alert(10, "PANIC", battery = Alert.Battery(70, false)),
            alert(11, "TRACKING_UPDATE", battery = Alert.Battery(60, true)),
            alert(12, "TRACKING_UPDATE", battery = null)
        )

        assertEquals(Alert.Battery(60, true), IncidentGrouper.incidentFor(10, alerts)!!.latestBattery)
        assertEquals(
            Alert.Battery(70, false),
            IncidentGrouper.incidentFor(10, listOf(alert(10, "PANIC", battery = Alert.Battery(70, false))))!!.latestBattery
        )
    }
}
