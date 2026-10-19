package com.example.guardband.data.repository

import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.model.User

/**
 * Decides whether a sign-in should write `users/{uid}`, and writes it.
 *
 * **Serves both sign-in paths** - Google sign-in and email login both route
 * through [ensureProfile]. (It was called `GoogleProfileProvisioning` while
 * only Google needed it.)
 *
 * Lives apart from [FirebaseAuthRepository] so it can be unit-tested: the
 * repository's own methods cannot be, because they need a real
 * [com.google.firebase.auth.FirebaseAuth] and the project declares no mocking
 * library.
 *
 * The rule exists because a sign-in only knows what the provider hands it.
 * Writing on every sign-in would put that thin version over whatever the user
 * already has. Limiting the write to records that do not exist yet is what
 * makes it safe to call on every sign-in.
 *
 * **A deletion marker is not an empty slot.** An account whose data was
 * deleted but whose sign-in survived reads [ProfileState.DELETION_PENDING],
 * and is never re-provisioned: that used to bring the account back with a
 * name and an email but no contacts and no consent. The deletion is resumed
 * instead, by the Home gate - see
 * [HomeViewModel][com.example.guardband.ui.home.HomeViewModel].
 */
internal object ProfileProvisioning {

    /**
     * Creates a minimal profile for [user] when it is missing, and otherwise
     * leaves it alone.
     *
     * Writes when [isNewUser] (the account was just created, so there is
     * certainly no record) or when the record is confirmed
     * [ProfileState.ABSENT]. A live profile and a deletion marker are both left
     * alone. A *failed* read writes nothing: it cannot tell an absent record
     * from an unreachable one, and skipping is the option that cannot destroy
     * data.
     *
     * Returns the write's [Result], or null when no write was attempted.
     * Callers deliberately ignore a failure: the Firebase session already
     * exists by this point, so failing the sign-in would leave the user signed
     * in and looking at an error. The next sign-in retries.
     *
     * Any [kotlinx.coroutines.CancellationException] from the repository
     * propagates: nothing here catches.
     */
    suspend fun ensureProfile(
        userProfileRepository: UserProfileRepository,
        user: User,
        isNewUser: Boolean
    ): Result<Unit>? {
        val missing = isNewUser ||
            userProfileRepository.profileState(user.id).getOrNull() == ProfileState.ABSENT
        if (!missing) return null

        return userProfileRepository.saveProfile(
            uid = user.id,
            name = user.name,
            email = user.email
        )
    }
}
