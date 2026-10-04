package com.example.guardband.data.repository

/**
 * The user's own profile record, separate from authentication.
 *
 * Only ever holds what the user typed during sign-up. Passwords, reset codes
 * and tokens are never written here.
 *
 * Same conventions as [AuthRepository]: suspend, and failures come back as
 * [Result.failure] carrying an [AuthError].
 */
interface UserProfileRepository {

    /** Creates or replaces the profile for [uid]. */
    suspend fun saveProfile(
        uid: String,
        name: String,
        email: String,
        location: String
    ): Result<Unit>
}
