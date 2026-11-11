package com.example.guardband.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BandIdInputTest {

    @Test
    fun `an anonymous uid is a band id, case kept`() {
        val uid = "aB3dE5fG7hI9jK1lM3nO5pQ7rS9t"

        assertEquals(uid, BandIdInput.deviceIdOf(uid))
    }

    @Test
    fun `the demo band's id is a band id`() {
        assertEquals("guardband-001", BandIdInput.deviceIdOf("guardband-001"))
    }

    @Test
    fun `spaces and line breaks around a pasted id are dropped`() {
        assertEquals("abcd1234", BandIdInput.deviceIdOf("  abcd1234\n"))
    }

    @Test
    fun `blank is not a band id`() {
        assertNull(BandIdInput.deviceIdOf("   "))
    }

    @Test
    fun `characters RTDB refuses in a key are refused here`() {
        listOf("ab.cd", "ab/cd", "ab#cd", "ab\$cd", "ab[cd]", "ab cd").forEach {
            assertNull(it, BandIdInput.deviceIdOf(it))
        }
    }

    @Test
    fun `too short or too long is not a band id`() {
        assertNull(BandIdInput.deviceIdOf("abc"))
        assertNull(BandIdInput.deviceIdOf("a".repeat(65)))
    }
}
