package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User

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

    /**
     * Creates or updates the profile fields for [uid].
     *
     * Merges `name` and `email` and leaves every other child of
     * `users/{uid}` alone. That matters now that
     * `users/{uid}/emergency_contacts` exists: this used to replace the whole
     * record, which would delete every emergency contact the user had.
     */
    suspend fun saveProfile(
        uid: String,
        name: String,
        email: String
    ): Result<Unit>

    /**
     * The stored profile for [uid], or `Result.success(null)` when there is no
     * record.
     *
     * A missing record is a success with null, not a failure: an account can legitimately exist without one, and
     * the caller needs to tell that apart from a read that did not work.
     */
    suspend fun fetchProfile(uid: String): Result<User?>

    /**
     * True when a profile record exists at `users/{uid}`.
     *
     * Kept alongside [fetchProfile] because [ProfileProvisioning] needs the
     * existence question answered without caring what is in the record.
     */
    suspend fun profileExists(uid: String): Result<Boolean>

    /**
     * Writes the profile and [contacts] for a brand-new account in **one**
     * atomic commit (D5).
     *
     * One multi-path write, so sign-up cannot half-succeed: either the profile
     * and every contact land, or nothing does. That is what makes the retry on
     * [SignUpContactsViewModel][com.example.guardband.ui.signup.SignUpContactsViewModel]
     * safe to run again - there is no partial state to reconcile.
     *
     * Each contact is validated and normalised first, so an invalid phone fails
     * the whole call before anything is sent. Only for a new account: the
     * contacts are written under fresh keys, so calling this on an account that
     * already has contacts would add duplicates rather than replace them.
     */
    suspend fun finalizeSignUp(
        uid: String,
        name: String,
        email: String,
        contacts: List<EmergencyContact>
    ): Result<Unit>
}
