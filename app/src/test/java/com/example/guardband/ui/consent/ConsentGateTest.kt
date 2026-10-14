package com.example.guardband.ui.consent

import com.example.guardband.data.model.ConsentRecord
import com.example.guardband.data.repository.UserProfileRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a missing record triggers the gate. No version comparison yet. */
class ConsentGateTest {

    @Test
    fun `no record needs consent`() {
        assertTrue(ConsentGate.needsConsent(null))
    }

    @Test
    fun `a record at 1_0 does not`() {
        assertFalse(ConsentGate.needsConsent(ConsentRecord(version = "1.0")))
    }

    @Test
    fun `a record at the current version does not`() {
        assertFalse(
            ConsentGate.needsConsent(ConsentRecord(version = UserProfileRepository.CONSENT_VERSION))
        )
    }

    /** Unreadable version, but a stored acceptance: not a re-prompt today. */
    @Test
    fun `a record with no version does not`() {
        assertFalse(ConsentGate.needsConsent(ConsentRecord(version = "")))
    }
}
