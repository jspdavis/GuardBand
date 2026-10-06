package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [UserProfileRepository] on the Realtime Database at `users/{uid}`.
 *
 * Writes use [DatabaseReference.updateChildren], never `setValue` on
 * `users/{uid}`: the node now also holds `emergency_contacts`, and replacing it
 * would delete every contact the user has. The uid is the key, so it is not
 * duplicated inside the record.
 *
 * Failures are mapped by [DatabaseError.getCode] through
 * [FirebaseDatabaseErrorMapper] - see its KDoc for why the listener forms are
 * used instead of `.await()`. A database error has no auth error code, so the
 * mapping is into [AuthError]'s vocabulary rather than out of a Firebase one.
 *
 * Proposed Security Rules for this path are in the Prompt 10 report; they are
 * not deployed yet, so a write only succeeds while the rules are permissive.
 *
 * Nothing here logs: not a uid, not an email, not a contact's phone number.
 */
class FirebaseUserProfileRepository(
    private val database: FirebaseDatabase
) : UserProfileRepository {

    override suspend fun saveProfile(
        uid: String,
        name: String,
        email: String
    ): Result<Unit> = profileResult {
        userRef(uid).update(
            mapOf(
                FIELD_NAME to name,
                FIELD_EMAIL to email
            )
        )
    }

    override suspend fun fetchProfile(uid: String): Result<User?> = profileResult {
        val snapshot = userRef(uid).readOnce()
        if (!snapshot.exists()) {
            null
        } else {
            User(
                id = uid,
                name = snapshot.child(FIELD_NAME).value?.toString().orEmpty(),
                email = snapshot.child(FIELD_EMAIL).value?.toString().orEmpty()
            )
        }
    }

    override suspend fun profileExists(uid: String): Result<Boolean> = profileResult {
        userRef(uid).readOnce().exists()
    }

    override suspend fun finalizeSignUp(
        uid: String,
        name: String,
        email: String,
        contacts: List<EmergencyContact>,
        consentVersion: String
    ): Result<Unit> = profileResult {
        val contactsRef = userRef(uid).child(ContactFields.NODE_EMERGENCY_CONTACTS)

        // Built against the root with absolute paths, which is what makes the
        // whole thing one commit. Note the per-contact paths: writing the
        // emergency_contacts parent as a map would replace that subtree, which
        // is harmless on a new account but a trap if this is ever reused.
        val updates = mutableMapOf<String, Any?>(
            "$NODE_USERS/$uid/$FIELD_NAME" to name,
            "$NODE_USERS/$uid/$FIELD_EMAIL" to email,
            "$NODE_USERS/$uid/$NODE_CONSENT/$FIELD_CONSENT_ACCEPTED" to true,
            "$NODE_USERS/$uid/$NODE_CONSENT/$FIELD_CONSENT_VERSION" to consentVersion,
            // Server time, not the handset's: a device clock can be wrong or
            // deliberately set, and this is the field that says when the user
            // actually agreed.
            "$NODE_USERS/$uid/$NODE_CONSENT/$FIELD_CONSENT_AT" to ServerValue.TIMESTAMP
        )

        contacts.forEach { contact ->
            // Validated before anything is sent, so one bad phone number fails
            // the call rather than committing a partial sign-up.
            val stored = ContactFields.validated(contact)
            val key = contactsRef.push().key ?: throw AuthError.Unknown(null)
            val path = "$NODE_USERS/$uid/${ContactFields.NODE_EMERGENCY_CONTACTS}/$key"
            updates[path] = ContactFields.toFieldMap(stored)
        }

        database.reference.update(updates)
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private fun userRef(uid: String): DatabaseReference =
        database.getReference(NODE_USERS).child(uid)

    /** [DatabaseReference.updateChildren], suspending, with the error code kept. */
    private suspend fun DatabaseReference.update(values: Map<String, Any?>): Unit =
        suspendCancellableCoroutine { continuation ->
            updateChildren(values) { error, _ ->
                if (!continuation.isActive) return@updateChildren
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        FirebaseDatabaseErrorMapper.mapToAuthError(error)
                    )
                }
            }
        }

    /** One-shot read, suspending, with the error code kept. */
    private suspend fun DatabaseReference.readOnce(): DataSnapshot =
        suspendCancellableCoroutine { continuation ->
            addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (continuation.isActive) continuation.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            FirebaseDatabaseErrorMapper.mapToAuthError(error)
                        )
                    }
                }
            })
        }

    /**
     * Runs [block] and maps anything it throws to an [AuthError].
     *
     * A [ContactError.Validation] from [ContactFields] is folded into
     * [AuthError.Unknown]: it can only come from [finalizeSignUp], whose
     * caller validated the same fields first, so reaching it means a bug rather
     * than something to word for the user.
     */
    private inline fun <T> profileResult(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(FirebaseAuthErrorMapper.map(e))
        }

    private companion object {
        const val NODE_USERS = "users"
        const val FIELD_NAME = "name"
        const val FIELD_EMAIL = "email"
        const val NODE_CONSENT = "consent"
        const val FIELD_CONSENT_ACCEPTED = "accepted"
        const val FIELD_CONSENT_VERSION = "version"
        const val FIELD_CONSENT_AT = "acceptedAt"
    }
}
