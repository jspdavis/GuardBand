package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * [ContactFields.validated]: phone normalization and the self-phone guard.
 */
class ContactFieldsTest {

    private fun contact(phone: String) = EmergencyContact(
        id = "",
        name = "Maria Santos",
        phone = phone,
        relationship = "Guardian",
        isEmergency = true
    )

    // ── Self-phone check ──────────────────────────────────────────────────────

    @Test
    fun `a contact whose phone matches the own phone is rejected`() {
        val own = "+639171234567"
        val result = runCatching { ContactFields.validated(contact(own), ownPhone = own) }
        assert(result.exceptionOrNull() is ContactError.SelfPhone) {
            "Expected SelfPhone but got ${result.exceptionOrNull()}"
        }
    }

    @Test
    fun `a contact whose phone matches after E164 normalization is rejected`() {
        // own is stored E.164; contact typed as local 09171234567 → normalises to same
        val own = "+639171234567"
        val result = runCatching { ContactFields.validated(contact("09171234567"), ownPhone = own) }
        assert(result.exceptionOrNull() is ContactError.SelfPhone) {
            "Expected SelfPhone but got ${result.exceptionOrNull()}"
        }
    }

    @Test
    fun `a different phone is accepted`() {
        val own = "+639171234567"
        val contact = ContactFields.validated(contact("+639179999999"), ownPhone = own)
        assertNotNull(contact)
        assertEquals("+639179999999", contact.phone)
    }

    @Test
    fun `no own phone skips the self-phone check`() {
        // ownPhone = null: no comparison, valid phone still passes
        val contact = ContactFields.validated(contact("+639171234567"), ownPhone = null)
        assertNotNull(contact)
    }

    @Test
    fun `an invalid phone still fails validation regardless of own-phone`() {
        val result = runCatching { ContactFields.validated(contact("not-a-phone"), ownPhone = null) }
        assert(result.exceptionOrNull() is ContactError.Validation) {
            "Expected Validation but got ${result.exceptionOrNull()}"
        }
    }
}
