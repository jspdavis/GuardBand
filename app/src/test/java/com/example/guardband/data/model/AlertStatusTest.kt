package com.example.guardband.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertStatusTest {

    @Test
    fun `fromWire parses every known status`() {
        assertEquals(AlertStatus.ACTIVE,       AlertStatus.fromWire("ACTIVE"))
        assertEquals(AlertStatus.ACKNOWLEDGED, AlertStatus.fromWire("ACKNOWLEDGED"))
        assertEquals(AlertStatus.RESOLVED,     AlertStatus.fromWire("RESOLVED"))
    }

    @Test
    fun `fromWire defaults to ACTIVE for an unknown value`() {
        assertEquals(AlertStatus.ACTIVE, AlertStatus.fromWire("CLOSED"))
    }

    @Test
    fun `fromWire defaults to ACTIVE for null`() {
        assertEquals(AlertStatus.ACTIVE, AlertStatus.fromWire(null))
    }

    @Test
    fun `fromWire is case sensitive so lowercase defaults to ACTIVE`() {
        assertEquals(AlertStatus.ACTIVE, AlertStatus.fromWire("active"))
    }

    @Test
    fun `ACTIVE is the default on a newly constructed Alert`() {
        val alert = Alert(sequenceId = 1, type = "PANIC", timestamp = "2026-10-01T00:00:00Z")
        assertEquals(AlertStatus.ACTIVE, alert.status)
    }
}
