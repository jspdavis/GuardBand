package com.example.guardband.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InputValidator.normalizePhoneToE164].
 *
 * No Robolectric: the phone rules are pure Kotlin and never reach
 * `android.util.Patterns`.
 */
class InputValidatorPhoneTest {

    private val stored = "+639171234567"

    // ── The three accepted Philippine spellings (D3) ──────────────────────────

    @Test
    fun `local 09 form is stored as E164`() {
        assertEquals(stored, InputValidator.normalizePhoneToE164("09171234567"))
    }

    @Test
    fun `country code without plus is stored as E164`() {
        assertEquals(stored, InputValidator.normalizePhoneToE164("639171234567"))
    }

    @Test
    fun `already E164 Philippine mobile is unchanged`() {
        assertEquals(stored, InputValidator.normalizePhoneToE164(stored))
    }

    @Test
    fun `all three spellings normalize to the same stored value`() {
        val results = listOf("09171234567", "639171234567", "+639171234567")
            .map(InputValidator::normalizePhoneToE164)
            .distinct()

        assertEquals(listOf(stored), results)
    }

    // ── Separators humans type ────────────────────────────────────────────────

    @Test
    fun `spaces are stripped`() {
        assertEquals(stored, InputValidator.normalizePhoneToE164("0917 123 4567"))
    }

    @Test
    fun `hyphens parentheses and dots are stripped`() {
        assertEquals(stored, InputValidator.normalizePhoneToE164("(0917) 123-4567"))
        assertEquals(stored, InputValidator.normalizePhoneToE164("+63.917.123.4567"))
        assertEquals(stored, InputValidator.normalizePhoneToE164("63-917-123-4567"))
    }

    // ── Other valid E164 passes through untouched ────────────────────────────

    @Test
    fun `a foreign E164 number is accepted as typed`() {
        assertEquals("+14155550123", InputValidator.normalizePhoneToE164("+1 415 555 0123"))
    }

    @Test
    fun `the shortest and longest E164 lengths are both accepted`() {
        assertEquals("+12345678", InputValidator.normalizePhoneToE164("+12345678"))
        assertEquals("+123456789012345", InputValidator.normalizePhoneToE164("+123456789012345"))
    }

    @Test
    fun `a Philippine landline is accepted with its country code`() {
        assertEquals("+6321234567", InputValidator.normalizePhoneToE164("+63 2 123 4567"))
    }

    // ── Rejected ──────────────────────────────────────────────────────────────

    @Test
    fun `blank is rejected`() {
        assertNull(InputValidator.normalizePhoneToE164(""))
        assertNull(InputValidator.normalizePhoneToE164("   "))
    }

    @Test
    fun `a local number without a country code is rejected`() {
        // Nothing to normalise it against: see normalizePhoneToE164's KDoc.
        assertNull(InputValidator.normalizePhoneToE164("021234567"))
    }

    @Test
    fun `a local Philippine mobile number of the wrong length is rejected`() {
        assertNull(InputValidator.normalizePhoneToE164("0917123456"))    // one short
        assertNull(InputValidator.normalizePhoneToE164("091712345678"))  // one long
    }

    @Test
    fun `a too-short Philippine mobile written with a plus survives as generic E164`() {
        // Not a bug, but the price of D3's "any other valid E.164" rule: once a
        // number carries a leading +, the only test left is E.164's own length
        // range, so a mistyped +63 mobile is stored as typed instead of being
        // normalised or refused. Nothing downstream can catch it either - the
        // Security Rules check the same pattern.
        assertEquals("+6391712345", InputValidator.normalizePhoneToE164("+6391712345"))
    }

    @Test
    fun `a plus number outside the E164 length range is rejected`() {
        assertNull(InputValidator.normalizePhoneToE164("+1234567"))
        assertNull(InputValidator.normalizePhoneToE164("+1234567890123456"))
    }

    @Test
    fun `letters are rejected rather than stripped`() {
        assertNull(InputValidator.normalizePhoneToE164("0917ABC4567"))
        assertNull(InputValidator.normalizePhoneToE164("+63917CALLME"))
        assertNull(InputValidator.normalizePhoneToE164("call me"))
    }

    @Test
    fun `a bare plus is rejected`() {
        assertNull(InputValidator.normalizePhoneToE164("+"))
    }

    @Test
    fun `a number with digits only and no leading zero or country code is rejected`() {
        assertNull(InputValidator.normalizePhoneToE164("9171234567"))
    }

    // ── The predicate agrees with the normalizer ──────────────────────────────

    @Test
    fun `isValidPhone agrees with normalizePhoneToE164`() {
        assertTrue(InputValidator.isValidPhone("09171234567"))
        assertFalse(InputValidator.isValidPhone("021234567"))
    }
}
