package com.example.guardband.data.repository

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [UserProfileRepository] on the Realtime Database at `users/{uid}`.
 *
 * Writes exactly three fields — `name`, `email`, `location` — with
 * [setValue][com.google.firebase.database.DatabaseReference.setValue], so a
 * re-registration replaces the record rather than merging into it. The uid is
 * the key, so it is not duplicated inside the record.
 *
 * Proposed Security Rules for this path are in the Prompt 08 report; they are
 * not deployed yet, so a write only succeeds while the rules are permissive.
 *
 * Reuses [FirebaseAuthErrorMapper] for its failures: a database error has no
 * auth error code, so anything other than a network failure becomes
 * [AuthError.Unknown].
 */
class FirebaseUserProfileRepository(
    private val database: FirebaseDatabase
) : UserProfileRepository {

    override suspend fun saveProfile(
        uid: String,
        name: String,
        email: String,
        location: String
    ): Result<Unit> =
        try {
            database.getReference(NODE_USERS)
                .child(uid)
                .setValue(
                    mapOf(
                        FIELD_NAME to name,
                        FIELD_EMAIL to email,
                        FIELD_LOCATION to location
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(FirebaseAuthErrorMapper.map(e))
        }

    override suspend fun profileExists(uid: String): Result<Boolean> =
        try {
            val snapshot = database.getReference(NODE_USERS).child(uid).get().await()
            Result.success(snapshot.exists())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(FirebaseAuthErrorMapper.map(e))
        }

    companion object {
        private const val NODE_USERS = "users"
        private const val FIELD_NAME = "name"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_LOCATION = "location"
    }
}
