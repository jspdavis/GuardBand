package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import kotlinx.coroutines.delay

/**
 * [ContactRepository] backed by [InMemoryStore].
 *
 * Like MockRepository today, contacts are one global list (not per user)
 * and neither call can fail.
 */
class InMemoryContactRepository : ContactRepository {

    override suspend fun getContacts(): Result<List<EmergencyContact>> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return Result.success(InMemoryStore.getContacts())
    }

    override suspend fun addContact(contact: EmergencyContact): Result<EmergencyContact> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return Result.success(InMemoryStore.addContact(contact))
    }
}
