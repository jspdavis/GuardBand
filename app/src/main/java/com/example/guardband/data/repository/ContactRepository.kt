package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact

/**
 * Emergency contacts for the current user.
 *
 * Same error and threading conventions as [AuthRepository].
 */
interface ContactRepository {
    suspend fun getContacts(): Result<List<EmergencyContact>>
    suspend fun addContact(contact: EmergencyContact): Result<EmergencyContact>

    /** Removes the contact with [contactId]. Fails if no such contact exists. */
    suspend fun deleteContact(contactId: String): Result<Unit>
}
