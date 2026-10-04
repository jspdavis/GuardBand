package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import kotlinx.coroutines.delay

/**
 * [ContactRepository] backed by [InMemoryStore].
 *
 * Contacts are one global list (not per user). Only [deleteContact] can fail,
 * when the id is unknown.
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

    override suspend fun deleteContact(contactId: String): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return if (InMemoryStore.removeContact(contactId)) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("That contact no longer exists."))
        }
    }
}
