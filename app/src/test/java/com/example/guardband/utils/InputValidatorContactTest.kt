package com.example.guardband.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contact name, relationship and count rules (D2). No Robolectric needed. */
class InputValidatorContactTest {

    // ── Name ──────────────────────────────────────────────────────────────────

    @Test
    fun `a normal name is accepted`() {
        assertTrue(InputValidator.isValidContactName("Jordan Lee"))
    }

    @Test
    fun `a blank name is rejected`() {
        assertFalse(InputValidator.isValidContactName(""))
        assertFalse(InputValidator.isValidContactName("   "))
    }

    @Test
    fun `a name is measured after trimming`() {
        val name = "a".repeat(InputValidator.MAX_CONTACT_NAME_LENGTH)

        assertTrue(InputValidator.isValidContactName("  $name  "))
    }

    @Test
    fun `a name at the limit is accepted and one over is rejected`() {
        assertTrue(
            InputValidator.isValidContactName("a".repeat(InputValidator.MAX_CONTACT_NAME_LENGTH))
        )
        assertFalse(
            InputValidator.isValidContactName("a".repeat(InputValidator.MAX_CONTACT_NAME_LENGTH + 1))
        )
    }

    // ── Relationship (optional) ───────────────────────────────────────────────

    @Test
    fun `a blank relationship is accepted because the field is optional`() {
        assertTrue(InputValidator.isValidContactRelationship(""))
        assertTrue(InputValidator.isValidContactRelationship("   "))
    }

    @Test
    fun `a relationship at the limit is accepted and one over is rejected`() {
        assertTrue(
            InputValidator.isValidContactRelationship(
                "a".repeat(InputValidator.MAX_RELATIONSHIP_LENGTH)
            )
        )
        assertFalse(
            InputValidator.isValidContactRelationship(
                "a".repeat(InputValidator.MAX_RELATIONSHIP_LENGTH + 1)
            )
        )
    }

    // ── Counts ────────────────────────────────────────────────────────────────

    @Test
    fun `the bounds are three and ten`() {
        assertEquals(3, InputValidator.MIN_CONTACTS)
        assertEquals(10, InputValidator.MAX_CONTACTS)
    }

    @Test
    fun `adding is allowed up to the cap and refused at it`() {
        assertTrue(InputValidator.canAddContact(0))
        assertTrue(InputValidator.canAddContact(InputValidator.MAX_CONTACTS - 1))
        assertFalse(InputValidator.canAddContact(InputValidator.MAX_CONTACTS))
    }

    @Test
    fun `adding is refused past the cap too`() {
        // Defensive: a stale count must not reopen the gate.
        assertFalse(InputValidator.canAddContact(InputValidator.MAX_CONTACTS + 1))
    }

    @Test
    fun `deleting is refused while it would leave fewer than the minimum`() {
        assertFalse(InputValidator.canDeleteContact(0))
        assertFalse(InputValidator.canDeleteContact(1))
        assertFalse(InputValidator.canDeleteContact(2))
        assertFalse(InputValidator.canDeleteContact(InputValidator.MIN_CONTACTS))
    }

    @Test
    fun `deleting is allowed once one above the minimum`() {
        assertTrue(InputValidator.canDeleteContact(InputValidator.MIN_CONTACTS + 1))
        assertTrue(InputValidator.canDeleteContact(InputValidator.MAX_CONTACTS))
    }
}
