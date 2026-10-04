package com.example.guardband.data.repository

import com.example.guardband.data.model.User

/**
 * Authentication, password reset and session.
 *
 * Failures are returned as [Result.failure] carrying an [AuthError]. The
 * exception has no message: each screen words its own errors, so ViewModels
 * map the [AuthError] to their `MSG_*` constants. (This is the opposite of
 * [ContactRepository] and [AlertRepository], which still carry the text.)
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
     * Creates the account and writes the profile at `users/{uid}`.
     * [name] and [location] come from sign-up steps 1 and 2.
     */
    suspend fun register(name: String, location: String, email: String, password: String): Result<User>

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
     * `location` is always empty: it lives at `users/{uid}` and would need an
     * async read. Profile and Track therefore fall back to "Location not set"
     * until that read lands (Prompt 09).
     */
    fun currentUser(): User?

    fun isLoggedIn(): Boolean
    fun signOut()
}
