package com.example.guardband.data.repository

import com.example.guardband.data.model.User

/**
 * Decides whether a Google sign-in should write `users/{uid}`, and writes it.
 *
 * Lives apart from [FirebaseAuthRepository] so it can be unit-tested: the
 * repository's own method cannot be, because it needs a real
 * [com.google.firebase.auth.FirebaseAuth] and the project declares no mocking
 * library.
 *
 * The rule exists because
 * [UserProfileRepository.saveProfile][UserProfileRepository.saveProfile]
 * *replaces* the record. Writing on every Google sign-in would wipe the
 * location a returning user had already set, so the write is limited to the
 * two cases where there is nothing to lose.
 */
internal object GoogleProfileProvisioning {

    /**
     * Creates the profile for [user] when it is missing, and otherwise leaves
     * it alone.
     *
     * Writes when [isNewUser] (Firebase just created the account, so there is
     * certainly no record) or when the record is confirmed absent. A *failed*
     * existence read writes nothing: it cannot tell an absent record from an
     * unreachable one, and skipping is the option that cannot destroy data.
     *
     * `location` is always written empty — Google does not supply one, and the
     * complete-profile steps fill it in next.
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
            userProfileRepository.profileExists(user.id).getOrNull() == false
        if (!missing) return null

        return userProfileRepository.saveProfile(
            uid = user.id,
            name = user.name,
            email = user.email,
            location = ""
        )
    }
}
