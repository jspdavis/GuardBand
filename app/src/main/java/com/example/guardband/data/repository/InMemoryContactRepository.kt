package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [ContactRepository] backed by [InMemoryStore].
 *
 * Kept as the test double for [FirebaseContactRepository] - nothing in the app
 * wires it any more. It enforces the same D2 bounds and returns the same
 * [ContactError]s, so the two are interchangeable; what it does not reproduce
 * is the transaction, because a single-threaded store cannot race with itself.
 *
 * Contacts are one global list, not per user, which is why
 * [ContactError.NotSignedIn] never comes out of here.
 */
class InMemoryContactRepository : ContactRepository {

    override fun observeContacts(): Flow<Result<List<EmergencyContact>>> = flow {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        emitAll(InMemoryStore.contacts.map { Result.success(it) })
    }

    override suspend fun addContact(contact: EmergencyContact): Result<EmergencyContact> =
        contactResult {
            val stored = ContactFields.validated(contact)
            if (!InputValidator.canAddContact(InMemoryStore.getContacts().size)) {
                throw ContactError.MaximumContacts
            }
            InMemoryStore.addContact(stored)
        }

    override suspend fun updateContact(contact: EmergencyContact): Result<Unit> = contactResult {
        if (contact.id.isBlank()) throw ContactError.NotFound
        val stored = ContactFields.validated(contact)
        if (!InMemoryStore.updateContact(stored)) throw ContactError.NotFound
    }

    override suspend fun deleteContact(contactId: String): Result<Unit> = contactResult {
        if (contactId.isBlank()) throw ContactError.NotFound
        // Same order as the Firebase implementation: a missing contact reports
        // that rather than a bound the user has not hit.
        if (InMemoryStore.getContacts().none { it.id == contactId }) {
            throw ContactError.NotFound
        }
        if (!InputValidator.canDeleteContact(InMemoryStore.getContacts().size)) {
            throw ContactError.MinimumContacts
        }
        if (!InMemoryStore.removeContact(contactId)) throw ContactError.NotFound
    }

    /** Mirrors [FirebaseContactRepository]'s wrapper, minus the Firebase mapping. */
    private inline fun <T> contactResult(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: ContactError) {
            Result.failure(e)
        }
}
