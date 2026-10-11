package com.example.guardband.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind the criteria list.
 *
 * Worth its own tests because the list a user reads and the gate on Submit are
 * the same value: if these drift, the screen either refuses a password it just
 * ticked green or accepts one it marked red.
 *
 * No Robolectric: every rule is `InputValidator`'s plain-Kotlin half, not the
 * `android.util.Patterns` one.
 */
class PasswordCriteriaTest {

    @Test
    fun `nothing typed is neutral, not failed`() {
        val criteria = PasswordCriteria.of("", "")

        assertTrue(criteria.untouched)
        assertFalse(criteria.longEnough)
        assertFalse(criteria.matches)
        assertFalse(criteria.allMet)
    }

    @Test
    fun `NONE agrees with nothing typed`() {
        assertEquals(PasswordCriteria.of("", ""), PasswordCriteria.NONE)
    }

    @Test
    fun `one keystroke is enough to stop being untouched`() {
        assertFalse(PasswordCriteria.of("a", "").untouched)
        assertFalse(PasswordCriteria.of("", "a").untouched)
    }

    /** Eight is the app's rule, and it is stricter than Firebase's six. */
    @Test
    fun `the length rule is the validator's minimum`() {
        assertFalse(PasswordCriteria.of("1234567", "1234567").longEnough)
        assertTrue(PasswordCriteria.of("12345678", "12345678").longEnough)
    }

    @Test
    fun `matching is exact`() {
        assertTrue(PasswordCriteria.of("Password1", "Password1").matches)
        assertFalse(PasswordCriteria.of("Password1", "password1").matches)
        assertFalse(PasswordCriteria.of("Password1", "Password1 ").matches)
    }

    /**
     * Two empty fields are equal, which would otherwise tick the match rule
     * green before the user has typed anything.
     */
    @Test
    fun `two empty fields do not count as matching`() {
        assertFalse(PasswordCriteria.of("", "").matches)
    }

    @Test
    fun `allMet needs both rules, not either`() {
        assertFalse(PasswordCriteria.of("short", "short").allMet)
        assertFalse(PasswordCriteria.of("longenough123", "different").allMet)
        assertTrue(PasswordCriteria.of("longenough123", "longenough123").allMet)
    }

    /** untouched is a rendering concern only, and must not leak into the gate. */
    @Test
    fun `untouched does not affect allMet`() {
        val met = PasswordCriteria(longEnough = true, matches = true, untouched = true)

        assertTrue(met.allMet)
    }
}
