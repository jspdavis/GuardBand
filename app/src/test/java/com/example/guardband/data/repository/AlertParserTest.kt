package com.example.guardband.data.repository

import com.example.guardband.data.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AlertParser], the wire-map to [com.example.guardband.data.model.Alert] step.
 *
 * No Robolectric and no mocking library: the parser takes a plain Map, which
 * is exactly the shape `DataSnapshot.getValue()` hands it, so the whole of
 * the parsing logic is reachable from a JVM test.
 */
class AlertParserTest {

    /** SCHEMA.md v1.0's own example payload. */
    private fun payload(
        schemaVersion: Any? = "1.0",
        deviceId: Any? = "guardband-001",
        type: Any? = "PANIC",
        timestamp: Any? = "2026-09-09T00:00:00Z",
        location: Any? = mapOf("lat" to 10.3157, "lng" to 123.8854, "accuracyMeters" to 8.5),
        battery: Any? = mapOf("percent" to 74L, "isCharging" to false),
        sequenceId: Any? = 1L
    ): Map<String, Any?> = mapOf(
        "schemaVersion" to schemaVersion,
        "deviceId" to deviceId,
        "type" to type,
        "timestamp" to timestamp,
        "location" to location,
        "battery" to battery,
        "sequenceId" to sequenceId
    )

    // ── A valid entry ─────────────────────────────────────────────────────────

    @Test
    fun `the schema example parses field for field`() {
        val alert = AlertParser.parse(payload())

        assertNotNull(alert)
        requireNotNull(alert)
        assertEquals("1.0", alert.schemaVersion)
        assertEquals("guardband-001", alert.deviceId)
        assertEquals("PANIC", alert.type)
        assertEquals(AlertType.PANIC, alert.alertType())
        assertEquals("2026-09-09T00:00:00Z", alert.timestamp)
        assertEquals(1L, alert.sequenceId)
        assertEquals(10.3157, alert.location!!.lat, 0.00001)
        assertEquals(123.8854, alert.location!!.lng, 0.00001)
        assertEquals(8.5, alert.location!!.accuracyMeters, 0.00001)
        assertEquals(74, alert.battery!!.percent)
        assertEquals(false, alert.battery!!.isCharging)
    }

    @Test
    fun `all four schema types parse to their enum`() {
        for (type in listOf("PANIC", "CHECKIN", "LOW_BATTERY", "TRACKING_UPDATE")) {
            val alert = AlertParser.parse(payload(type = type))
            assertEquals(type, AlertParser.parse(payload(type = type))?.type)
            assertEquals(AlertType.valueOf(type), alert?.alertType())
        }
    }

    // ── D5: schemaVersion is read as a String either way ──────────────────────

    @Test
    fun `schemaVersion survives as a string`() {
        assertEquals("1.0", AlertParser.parse(payload(schemaVersion = "1.0"))?.schemaVersion)
    }

    @Test
    fun `schemaVersion written as a bare number still parses`() {
        // D5: "1.0" and 1 must both work.
        assertEquals("1", AlertParser.parse(payload(schemaVersion = 1L))?.schemaVersion)
    }

    @Test
    fun `a missing schemaVersion does not drop the alert`() {
        val alert = AlertParser.parse(payload(schemaVersion = null))
        assertNotNull(alert)
        assertEquals("", alert?.schemaVersion)
    }

    // ── Unknown types are kept, not dropped ───────────────────────────────────

    @Test
    fun `an unknown type is kept raw and typed as null`() {
        val alert = AlertParser.parse(payload(type = "FALL_DETECTED"))

        assertNotNull(alert)
        assertEquals("FALL_DETECTED", alert?.type)
        assertNull(alert?.alertType())
    }

    @Test
    fun `type matching is case sensitive so lowercase is unknown`() {
        assertNull(AlertParser.parse(payload(type = "panic"))?.alertType())
    }

    // ── Malformed: the three required fields ──────────────────────────────────

    @Test
    fun `a missing sequenceId is malformed`() {
        assertNull(AlertParser.parse(payload(sequenceId = null)))
    }

    @Test
    fun `a non-numeric sequenceId is malformed`() {
        assertNull(AlertParser.parse(payload(sequenceId = "1")))
    }

    @Test
    fun `a missing type is malformed`() {
        assertNull(AlertParser.parse(payload(type = null)))
    }

    @Test
    fun `a blank type is malformed`() {
        assertNull(AlertParser.parse(payload(type = "   ")))
    }

    @Test
    fun `a missing timestamp is malformed`() {
        assertNull(AlertParser.parse(payload(timestamp = null)))
    }

    @Test
    fun `a node that is not a map at all is malformed`() {
        assertNull(AlertParser.parse(null))
        assertNull(AlertParser.parse("PANIC"))
        assertNull(AlertParser.parse(42L))
        assertNull(AlertParser.parse(listOf("PANIC")))
    }

    // ── Salvaged: the optional branches ───────────────────────────────────────

    @Test
    fun `a missing location leaves the alert with a null location`() {
        val alert = AlertParser.parse(payload(location = null))
        assertNotNull(alert)
        assertNull(alert?.location)
    }

    @Test
    fun `a location missing a coordinate is dropped rather than half-read`() {
        val alert = AlertParser.parse(payload(location = mapOf("lat" to 10.3157)))
        assertNotNull(alert)
        assertNull(alert?.location)
    }

    @Test
    fun `a location without accuracy defaults it to zero`() {
        val alert = AlertParser.parse(
            payload(location = mapOf("lat" to 10.3157, "lng" to 123.8854))
        )
        val location = alert?.location
        assertNotNull(location)
        requireNotNull(location)
        assertEquals(0.0, location.accuracyMeters, 0.00001)
    }

    @Test
    fun `a missing battery leaves the alert with a null battery`() {
        val alert = AlertParser.parse(payload(battery = null))
        assertNotNull(alert)
        assertNull(alert?.battery)
    }

    @Test
    fun `a battery without isCharging defaults it to false`() {
        val alert = AlertParser.parse(payload(battery = mapOf("percent" to 20L)))
        assertEquals(20, alert?.battery?.percent)
        assertEquals(false, alert?.battery?.isCharging)
    }

    @Test
    fun `integer coordinates parse as doubles`() {
        // RTDB hands back a Long when the written number had no fraction.
        val location = AlertParser.parse(payload(location = mapOf("lat" to 10L, "lng" to 123L)))
            ?.location
        assertNotNull(location)
        requireNotNull(location)
        assertEquals(10.0, location.lat, 0.00001)
        assertEquals(123.0, location.lng, 0.00001)
    }

    @Test
    fun `a missing deviceId does not drop the alert`() {
        val alert = AlertParser.parse(payload(deviceId = null))
        assertNotNull(alert)
        assertEquals("", alert?.deviceId)
    }
}
