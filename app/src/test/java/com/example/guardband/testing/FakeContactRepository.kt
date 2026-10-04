package com.example.guardband.testing

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.repository.ContactRepository

/** Records what was added, so sign-up tests can assert the contact was saved. */
class FakeContactRepository : ContactRepository {

    val added = mutableListOf<EmergencyContact>()

    override suspend fun getContacts(): Result<List<EmergencyContact>> = Result.success(added.toList())

    override suspend fun addContact(contact: EmergencyContact): Result<EmergencyContact> {
        added += contact
        return Result.success(contact)
    }

    override suspend fun deleteContact(contactId: String): Result<Unit> {
        added.removeAll { it.id == contactId }
        return Result.success(Unit)
    }
}
