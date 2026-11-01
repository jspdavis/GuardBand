package com.example.guardband.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleTest {

    @Test
    fun `fromWire parses GUARDIAN`() {
        assertEquals(Role.GUARDIAN, Role.fromWire("GUARDIAN"))
    }

    @Test
    fun `fromWire parses DEPENDENT`() {
        assertEquals(Role.DEPENDENT, Role.fromWire("DEPENDENT"))
    }

    @Test
    fun `fromWire returns null for an unknown value`() {
        assertNull(Role.fromWire("ADMIN"))
    }

    @Test
    fun `fromWire returns null for null`() {
        assertNull(Role.fromWire(null))
    }

    @Test
    fun `fromWire is case sensitive so lowercase returns null`() {
        assertNull(Role.fromWire("guardian"))
        assertNull(Role.fromWire("dependent"))
    }

    @Test
    fun `name is the wire value for both roles`() {
        assertEquals("GUARDIAN", Role.GUARDIAN.name)
        assertEquals("DEPENDENT", Role.DEPENDENT.name)
    }
}
