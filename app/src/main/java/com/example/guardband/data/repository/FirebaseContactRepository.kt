package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.utils.InputValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [ContactRepository] on the Realtime Database at
 * `users/{uid}/emergency_contacts/{pushId}`.
 *
 * The uid comes from the live session on every call, never from a parameter, so
 * no caller can write into another user's record.
 *
 * **Every mutation is a transaction** on the `emergency_contacts` node, not a
 * plain write to one child. The bounds in D2 are counts over siblings, and
 * Security Rules cannot count children, so a check-then-write would let two
 * devices both pass the cap, or both delete down below the minimum. A
 * transaction makes the count and the mutation commit together.
 *
 * Failures are mapped by [DatabaseError.getCode] through
 * [FirebaseDatabaseErrorMapper] - see its KDoc for why the listener forms are
 * used instead of `.await()`.
 *
 * Nothing here logs: not a uid, not a contact name, not a phone number.
 *
 * @param database the shared instance from
 *   [FirebaseProvider][com.example.guardband.data.FirebaseProvider].
 * @param auth read for the current uid only; no sign-in call is made here.
 */
class FirebaseContactRepository(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : ContactRepository {

    override fun observeContacts(): Flow<Result<List<EmergencyContact>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // A real failure, not an empty list: the screen must be able to
            // tell "you have no contacts" from "we could not look".
            send(Result.failure(ContactError.NotSignedIn))
            close()
            return@callbackFlow
        }

        val ref = contactsRef(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(Result.success(ContactFields.fromSnapshot(snapshot)))
            }

            // The flow stays open: the listener is still attached, so a
            // reconnect or a rules fix can make the next emission succeed.
            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(FirebaseDatabaseErrorMapper.map(error)))
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun addContact(contact: EmergencyContact): Result<EmergencyContact> =
        contactResult {
            val ref = contactsRef(requireUid())
            val stored = ContactFields.validated(contact)
            val key = ref.push().key ?: throw ContactError.Unknown(null)

            ref.mutate { data ->
                if (!InputValidator.canAddContact(data.childrenCount.toInt())) {
                    return@mutate ContactError.MaximumContacts
                }
                data.child(key).value = ContactFields.toFieldMap(stored)
                null
            }

            stored.copy(id = key)
        }

    override suspend fun updateContact(contact: EmergencyContact): Result<Unit> = contactResult {
        // A blank id means this contact was never stored, which the user sees
        // as the same thing as one that is gone.
        if (contact.id.isBlank()) throw ContactError.NotFound

        val ref = contactsRef(requireUid())
        val stored = ContactFields.validated(contact)

        ref.mutate { data ->
            val child = data.child(contact.id)
            if (child.value == null) {
                return@mutate ContactError.NotFound
            }
            // Replaces all three fields, so a relationship the user cleared is
            // cleared in the database too rather than left behind.
            child.value = ContactFields.toFieldMap(stored)
            null
        }
    }

    override suspend fun deleteContact(contactId: String): Result<Unit> = contactResult {
        if (contactId.isBlank()) throw ContactError.NotFound
        val ref = contactsRef(requireUid())

        ref.mutate { data ->
            when {
                // Checked before the minimum, so deleting an id that is already
                // gone reports that, not a bound the user has not hit.
                data.child(contactId).value == null -> ContactError.NotFound

                !InputValidator.canDeleteContact(data.childrenCount.toInt()) ->
                    ContactError.MinimumContacts

                else -> {
                    data.child(contactId).value = null
                    null
                }
            }
        }
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private fun contactsRef(uid: String): DatabaseReference =
        database.getReference(NODE_USERS)
            .child(uid)
            .child(ContactFields.NODE_EMERGENCY_CONTACTS)

    private fun requireUid(): String = auth.currentUser?.uid ?: throw ContactError.NotSignedIn

    /**
     * Runs [change] against this node as a transaction and suspends until it
     * commits.
     *
     * [change] returns the [ContactError] that should abort, or null to commit.
     * It may be called more than once if the node changes underneath, so it
     * must stay free of side effects.
     */
    private suspend fun DatabaseReference.mutate(
        change: (MutableData) -> ContactError?
    ): Unit = suspendCancellableCoroutine { continuation ->
        // Written on each doTransaction call and read in onComplete, so a retry
        // overwrites it and the last attempt's verdict is the one reported.
        var rejection: ContactError? = null

        runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                rejection = change(currentData)
                return if (rejection == null) {
                    Transaction.success(currentData)
                } else {
                    Transaction.abort()
                }
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (!continuation.isActive) return

                val failure = when {
                    // Our own verdict first: an abort also arrives as
                    // committed == false, and MinimumContacts tells the user
                    // far more than "unknown" would.
                    rejection != null -> rejection
                    error != null -> FirebaseDatabaseErrorMapper.map(error)
                    !committed -> ContactError.Unknown(null)
                    else -> null
                }

                if (failure == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(failure)
                }
            }
        })
    }

    /**
     * Runs [block] and maps anything it throws to a [ContactError].
     *
     * [CancellationException] is rethrown so a cancelled viewModelScope stays
     * cancelled instead of being reported to the user as a failure.
     */
    private inline fun <T> contactResult(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(FirebaseDatabaseErrorMapper.map(e))
        }

    private companion object {
        const val NODE_USERS = "users"
    }
}
