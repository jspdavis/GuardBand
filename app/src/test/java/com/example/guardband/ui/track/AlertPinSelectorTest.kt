package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which recent alerts get a pin on the Track map. Pure: no Android, no
 * Robolectric.
 */
class AlertPinSelectorTest {

    /** Cebu City, the mock sender's area. */
    private val baseLat = 10.3157
    private val baseLng = 123.8854

    /** About 111 m of latitude: far enough apart that no two spots merge. */
    private val spotStep = 0.001

    /** About 1.1 m of latitude per unit, for "the same spot" tests. */
    private val metreStep = 0.00001

    private fun spot(index: Int) = Alert.Location(baseLat + index * spotStep, baseLng, 8.5)

    private fun alert(
        sequenceId: Long,
        type: String = "PANIC",
        location: Alert.Location? = spot(sequenceId.toInt()),
        timestamp: String = "2026-10-27T08:00:00Z"
    ) = Alert(
        schemaVersion = "1.0",
        deviceId = "guardband-001",
        type = type,
        timestamp = timestamp,
        location = location,
        battery = Alert.Battery(64, false),
        sequenceId = sequenceId
    )

    private fun ids(pins: List<AlertPin>) = pins.map { it.alert.sequenceId }

    @Test
    fun `an empty list has no pins`() {
        assertEquals(emptyList<AlertPin>(), AlertPinSelector.select(emptyList()))
    }

    @Test
    fun `fewer than ten alerts are all pinned, newest first`() {
        val pins = AlertPinSelector.select((1L..4L).map { alert(it) })

        assertEquals(listOf(4L, 3L, 2L, 1L), ids(pins))
    }

    @Test
    fun `more than ten alerts keep only the newest ten`() {
        val pins = AlertPinSelector.select((1L..15L).map { alert(it) })

        assertEquals(AlertPinSelector.MAX_PINS, pins.size)
        assertEquals((15L downTo 6L).toList(), ids(pins))
    }

    @Test
    fun `each pinned type keeps its type and its own coordinates`() {
        val pins = AlertPinSelector.select(
            listOf(alert(1, "PANIC"), alert(2, "CHECKIN"), alert(3, "LOW_BATTERY"))
        )

        assertEquals(
            listOf(AlertType.LOW_BATTERY, AlertType.CHECKIN, AlertType.PANIC),
            pins.map { it.type }
        )
        assertEquals(spot(3).lat, pins[0].lat, 0.0)
        assertEquals(spot(3).lng, pins[0].lng, 0.0)
    }

    @Test
    fun `tracking updates are never pinned`() {
        val pins = AlertPinSelector.select(
            listOf(alert(1, "PANIC"), alert(2, "TRACKING_UPDATE"), alert(3, "TRACKING_UPDATE"))
        )

        assertEquals(listOf(1L), ids(pins))
    }

    @Test
    fun `unknown types are never pinned, though the lists show them`() {
        val pins = AlertPinSelector.select(
            listOf(alert(1, "CHECKIN"), alert(2, "FALL_DETECTED"), alert(3, ""))
        )

        assertEquals(listOf(1L), ids(pins))
    }

    @Test
    fun `an alert with no location is not pinned`() {
        val pins = AlertPinSelector.select(listOf(alert(1), alert(2, location = null)))

        assertEquals(listOf(1L), ids(pins))
    }

    @Test
    fun `invalid coordinates are not pinned`() {
        val bad = listOf(
            Alert.Location(Double.NaN, baseLng, 5.0),
            Alert.Location(baseLat, Double.POSITIVE_INFINITY, 5.0),
            Alert.Location(91.0, baseLng, 5.0),
            Alert.Location(-90.5, baseLng, 5.0),
            Alert.Location(baseLat, 180.5, 5.0),
            Alert.Location(baseLat, -181.0, 5.0),
            // What a GPS module reports before its first fix.
            Alert.Location(0.0, 0.0, 0.0)
        )
        val alerts = bad.mapIndexed { i, location -> alert(i + 10L, location = location) }

        assertEquals(emptyList<AlertPin>(), AlertPinSelector.select(alerts))
        assertEquals(listOf(1L), ids(AlertPinSelector.select(alerts + alert(1))))
    }

    @Test
    fun `the edges of the range, and a zero on one axis only, are usable`() {
        assertTrue(AlertPinSelector.isUsable(Alert.Location(90.0, 180.0, 0.0)))
        assertTrue(AlertPinSelector.isUsable(Alert.Location(-90.0, -180.0, 0.0)))
        assertTrue(AlertPinSelector.isUsable(Alert.Location(0.0, baseLng, 0.0)))
        assertTrue(AlertPinSelector.isUsable(Alert.Location(baseLat, 0.0, 0.0)))
        assertFalse(AlertPinSelector.isUsable(Alert.Location(0.0, 0.0, 0.0)))
    }

    @Test
    fun `several alerts at the same spot keep only the newest`() {
        val here = spot(0)
        val pins = AlertPinSelector.select(
            listOf(
                alert(1, "PANIC", here),
                alert(2, "CHECKIN", here),
                alert(3, "LOW_BATTERY", here)
            )
        )

        assertEquals(listOf(3L), ids(pins))
        assertEquals(AlertType.LOW_BATTERY, pins.single().type)
    }

    @Test
    fun `alerts a few metres apart share a pin, and ones further apart do not`() {
        val origin = Alert.Location(baseLat, baseLng, 5.0)
        // About 5.6 m and 8.9 m north of the origin: within the 10 m radius.
        val near = Alert.Location(baseLat + 5 * metreStep, baseLng, 5.0)
        val nearer = Alert.Location(baseLat + 8 * metreStep, baseLng, 5.0)
        // About 13 m: its own pin.
        val apart = Alert.Location(baseLat + 12 * metreStep, baseLng, 5.0)

        val pins = AlertPinSelector.select(
            listOf(alert(4, location = origin), alert(3, location = near), alert(2, location = nearer), alert(1, location = apart))
        )

        assertEquals(listOf(4L, 1L), ids(pins))
    }

    @Test
    fun `a dropped duplicate does not count toward the ten`() {
        // Twelve alerts, the newest two at the same spot as the third newest.
        val alerts = (1L..10L).map { alert(it) } +
            alert(11, location = spot(10)) +
            alert(12, location = spot(10))

        val pins = AlertPinSelector.select(alerts)

        assertEquals(AlertPinSelector.MAX_PINS, pins.size)
        assertEquals(listOf(12L) + (9L downTo 1L), ids(pins))
    }

    @Test
    fun `excluded types do not count toward the ten either`() {
        val alerts = (1L..10L).map { alert(it) } +
            (11L..20L).map { alert(it, "TRACKING_UPDATE") }

        assertEquals((10L downTo 1L).toList(), ids(AlertPinSelector.select(alerts)))
    }

    @Test
    fun `order follows sequenceId, whatever the timestamps or the input order say`() {
        // The band's clock jumped: the newest alert carries the oldest time.
        val alerts = listOf(
            alert(2, timestamp = "2026-10-27T09:00:00Z"),
            alert(3, timestamp = "2026-10-27T07:00:00Z"),
            alert(1, timestamp = "2026-10-27T10:00:00Z")
        )

        assertEquals(listOf(3L, 2L, 1L), ids(AlertPinSelector.select(alerts)))
    }

    @Test
    fun `at the same spot, the newest by sequenceId wins over a later timestamp`() {
        val here = spot(0)
        val alerts = listOf(
            alert(1, "PANIC", here, timestamp = "2026-10-27T10:00:00Z"),
            alert(2, "CHECKIN", here, timestamp = "2026-10-27T07:00:00Z")
        )

        assertEquals(listOf(2L), ids(AlertPinSelector.select(alerts)))
    }

    @Test
    fun `a repeated sequenceId is pinned once`() {
        assertEquals(listOf(1L), ids(AlertPinSelector.select(listOf(alert(1), alert(1)))))
    }

    @Test
    fun `a pin under the band's marker is dropped, so the face wins`() {
        val pins = AlertPinSelector.select(
            listOf(alert(1), alert(2), alert(3)),
            bandLocation = Alert.Location(spot(2).lat + 3 * metreStep, spot(2).lng, 5.0)
        )

        assertEquals(listOf(3L, 1L), ids(pins))
    }

    @Test
    fun `with no band marker, or an unusable one, nothing is hidden`() {
        val alerts = listOf(alert(1), alert(2))

        assertEquals(listOf(2L, 1L), ids(AlertPinSelector.select(alerts, bandLocation = null)))
        assertEquals(
            listOf(2L, 1L),
            ids(AlertPinSelector.select(alerts, bandLocation = Alert.Location(0.0, 0.0, 0.0)))
        )
    }

    @Test
    fun `distance is about 11 m per ten-thousandth of a degree of latitude`() {
        val d = AlertPinSelector.distanceMeters(baseLat, baseLng, baseLat + 0.0001, baseLng)

        assertEquals(11.1, d, 0.1)
        assertEquals(0.0, AlertPinSelector.distanceMeters(baseLat, baseLng, baseLat, baseLng), 0.0)
    }
}
