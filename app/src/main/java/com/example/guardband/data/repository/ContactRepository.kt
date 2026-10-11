package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import kotlinx.coroutines.flow.Flow

/**
 * Emergency contacts for the signed-in user (FR-06).
 *
 * Failures are returned as [Result.failure] carrying a [ContactError], which
 * has no message: each screen words its own errors. Suspend functions may be
 * called from the main thread (e.g. viewModelScope).
 *
 * The bounds from [com.example.guardband.utils.InputValidator] - a minimum of
 * [MIN_CONTACTS][com.example.guardband.utils.InputValidator.MIN_CONTACTS] and a
 * cap of [MAX_CONTACTS][com.example.guardband.utils.InputValidator.MAX_CONTACTS]
 * - are enforced **here**, not only in the dialog. Security Rules cannot count
 * children, so this interface is the last line that can.
 */
interface ContactRepository {

    /**
     * The user's contacts, re-emitted on every change.
     *
     * Emits once the first read completes and again whenever the data changes.
     * A failed read is emitted as [Result.failure] rather than swallowed into
     * an empty list, so the screen can tell "no contacts" from "could not
     * load". Same shape as [AlertRepository]'s flows.
     */
    fun observeContacts(): Flow<Result<List<EmergencyContact>>>

    /**
     * Stores [contact] under a new key and returns it with that key in
     * [EmergencyContact.id].
     *
     * The phone is normalised to E.164 before it is written; a number
     * [normalizePhoneToE164][com.example.guardband.utils.InputValidator.normalizePhoneToE164]
     * rejects fails with [ContactError.Validation]. Fails with
     * [ContactError.MaximumContacts] at the cap.
     */
    suspend fun addContact(contact: EmergencyContact): Result<EmergencyContact>

    /**
     * Replaces the stored fields of [EmergencyContact.id] with [contact]'s.
     *
     * Validated and normalised exactly like [addContact]. Fails with
     * [ContactError.NotFound] if the contact is gone.
     */
    suspend fun updateContact(contact: EmergencyContact): Result<Unit>

    /**
     * Removes the contact with [contactId].
     *
     * Fails with [ContactError.MinimumContacts] when the delete would leave
     * fewer than the minimum, and [ContactError.NotFound] when there is no such
     * contact.
     */
    suspend fun deleteContact(contactId: String): Result<Unit>
}
