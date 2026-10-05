package com.example.guardband.data.repository

import com.example.guardband.data.model.GoogleSignInOutcome
import com.example.guardband.data.model.User

/**
 * Authentication, password reset and session.
 *
 * Failures are returned as [Result.failure] carrying an [AuthError]. The
 * exception has no message: each screen words its own errors, so ViewModels
 * map the [AuthError] to their `MSG_*` constants. [ContactRepository] follows
 * the same rule; [AlertRepository] is the one that still carries the text.
 *
 * Suspend functions may be called from the main thread (e.g. viewModelScope);
 * implementations switch threads themselves if they need to.
 *
 * Password reset is Firebase's own hosted flow: [sendPasswordReset] emails a
 * link and the user finishes in a browser. There is deliberately no verify-code
 * or set-new-password call — the app never sees a reset code.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>

    /**
     * Creates the account and stores [name] as the Firebase display name.
     *
     * Writes **nothing** to the database. The profile and the emergency
     * contacts are written afterwards by
     * [UserProfileRepository.finalizeSignUp], as one atomic commit.
     *
     * The split is deliberate. When the two were one call, a failed profile
     * write failed the whole thing with the account already created, and the
     * only retry the screen had went back through account creation - which then
     * failed as `ERROR_EMAIL_ALREADY_IN_USE`, leaving that address unable to
     * finish registering at all. With two calls the retry can re-run just the
     * write, and no comment has to be trusted to keep it that way.
     *
     * Note this signs the new user in, so a session exists from here on even if
     * the profile write never succeeds.
     */
    suspend fun createAccount(name: String, email: String, password: String): Result<User>

    /**
     * Exchanges a Google ID token for a Firebase session.
     *
     * [idToken] is obtained in the UI layer by
     * [GoogleIdTokenProvider][com.example.guardband.ui.auth.GoogleIdTokenProvider],
     * because Credential Manager needs an Activity. This layer only ever sees
     * the token string, never a Credential Manager type.
     *
     * The returned [GoogleSignInOutcome.isNewUser] tells the caller whether the
     * account was just created, so the UI can route a first-time user through
     * the remaining sign-up steps.
     *
     * Fails with [AuthError.AccountExistsWithDifferentCredential] when the
     * address already has an account made with another provider.
     */
    suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInOutcome>

    /**
     * Asks Firebase to email a password-reset link.
     *
     * Callers must treat [AuthError.NoSuchUser] as success: surfacing it would
     * let the screen be used to test which emails have accounts.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * The signed-in user, or null. Synchronous, so it is safe in a ViewModel's
     * constructor.
     *
     * Fills `id`, `name` (the Firebase display name) and `email` only.
     * `location` is always empty, because it lives at `users/{uid}` and needs
     * an async read: a screen that shows a location reads it with
     * [UserProfileRepository.fetchProfile] and uses this only as the fallback
     * for when no record exists yet.
     */
    fun currentUser(): User?

    fun isLoggedIn(): Boolean
    fun signOut()
}
