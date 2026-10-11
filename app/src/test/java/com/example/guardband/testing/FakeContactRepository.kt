package com.example.guardband.testing

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.repository.ContactError
import com.example.guardband.data.repository.ContactRepository
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [ContactRepository] holding contacts in a flow, so a test can assert what was
 * stored and can push a change at a collector mid-test.
 *
 * Enforces the same D2 bounds as the real implementations, because a ViewModel
 * test needs the repository to be the thing that says no. Each `*Result`
 * property lets a test pin a failure instead.
 */
class FakeContactRepository(
    initial: List<EmergencyContact> = emptyList(),
    var addResult: Result<EmergencyContact>? = null,
    var updateResult: Result<Unit>? = null,
    var deleteResult: Result<Unit>? = null
) : ContactRepository {

    private val contacts = MutableStateFlow(initial)

    /** Emitted instead of the contacts when a test wants the read to fail. */
    var observeResult: Result<List<EmergencyContact>>? = null

    /** Everything ever passed to [addContact], in call order. */
    val added = mutableListOf<EmergencyContact>()

    /** Everything ever passed to [updateContact], in call order. */
    val updated = mutableListOf<EmergencyContact>()

    /** Every id ever passed to [deleteContact], in call order. */
    val deleted = mutableListOf<String>()

    /** The current stored list, for assertions. */
    val stored: List<EmergencyContact> get() = contacts.value

    override fun observeContacts(): Flow<Result<List<EmergencyContact>>> =
        contacts.map { observeResult ?: Result.success(it) }

    override suspend fun addContact(contact: EmergencyContact): Result<EmergencyContact> {
        added += contact
        addResult?.let { return it }

        if (!InputValidator.canAddContact(contacts.value.size)) {
            return Result.failure(ContactError.MaximumContacts)
        }
        val saved = contact.copy(id = "fake-${contacts.value.size + 1}")
        contacts.value = contacts.value + saved
        return Result.success(saved)
    }

    override suspend fun updateContact(contact: EmergencyContact): Result<Unit> {
        updated += contact
        updateResult?.let { return it }

        if (contacts.value.none { it.id == contact.id }) {
            return Result.failure(ContactError.NotFound)
        }
        contacts.value = contacts.value.map { if (it.id == contact.id) contact else it }
        return Result.success(Unit)
    }

    override suspend fun deleteContact(contactId: String): Result<Unit> {
        deleted += contactId
        deleteResult?.let { return it }

        if (contacts.value.none { it.id == contactId }) {
            return Result.failure(ContactError.NotFound)
        }
        if (!InputValidator.canDeleteContact(contacts.value.size)) {
            return Result.failure(ContactError.MinimumContacts)
        }
        contacts.value = contacts.value.filterNot { it.id == contactId }
        return Result.success(Unit)
    }

    companion object {
        /** [MIN_CONTACTS][InputValidator.MIN_CONTACTS] stored contacts: deletes are allowed off this. */
        fun atMinimum(): List<EmergencyContact> = List(InputValidator.MIN_CONTACTS) { index ->
            EmergencyContact(
                id = "seed-$index",
                name = "Contact $index",
                phone = "+63917123456$index",
                relationship = "Friend"
            )
        }

        /** [MAX_CONTACTS][InputValidator.MAX_CONTACTS] stored contacts: adds are refused. */
        fun atCap(): List<EmergencyContact> = List(InputValidator.MAX_CONTACTS) { index ->
            EmergencyContact(
                id = "seed-$index",
                name = "Contact $index",
                phone = "+6391712345%02d".format(index),
                relationship = "Friend"
            )
        }
    }
}
