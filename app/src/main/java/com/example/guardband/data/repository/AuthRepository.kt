package com.example.guardband.data.repository

import com.example.guardband.data.model.GoogleSignInOutcome
import com.example.guardband.data.model.SignInProvider
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
     * Fills `id`, `name` (the Firebase display name) and `email`, which is
     * the whole of [User]. A screen wanting the stored record reads it with
     * [UserProfileRepository.fetchProfile] and uses this as the fallback for
     * when no record exists yet.
     */
    fun currentUser(): User?

    /**
     * Which providers the signed-in account can be reached by, or an empty set
     * when there is no session.
     *
     * Synchronous, like [currentUser], so a ViewModel can branch on it without
     * a coroutine. It answers "what must I ask the user for to re-authenticate
     * them", which is the only thing it is used for.
     *
     * An account made by this app has exactly one provider: Firebase's
     * one-account-per-email setting is what turns a second method into
     * [AuthError.AccountExistsWithDifferentCredential] instead of a link. A
     * set is returned anyway, because a linked account made elsewhere would
     * report both and the caller has to choose.
     *
     * An unrecognised provider id is dropped, so the set can be empty for a
     * live session. The caller must treat that as "cannot re-authenticate"
     * rather than assuming a default.
     */
    fun currentUserProviders(): Set<SignInProvider>

    /**
     * Proves the signed-in user still knows their password, so that
     * [deleteAccount] is allowed.
     *
     * The email comes from the **live session**, never from the screen: the
     * same reasoning complete-profile mode uses, so a stale field cannot
     * re-authenticate against the wrong record. Only [password] is passed in,
     * and it is never stored, logged or written.
     *
     * Fails with [AuthError.InvalidCredentials] for a wrong password and
     * [AuthError.NotSignedIn] when the session has gone.
     */
    suspend fun reauthenticateWithPassword(password: String): Result<Unit>

    /**
     * The Google equivalent of [reauthenticateWithPassword]: proves the user
     * still holds the account by presenting a fresh ID token.
     *
     * [idToken] comes from
     * [GoogleIdTokenProvider][com.example.guardband.ui.auth.GoogleIdTokenProvider]
     * in the UI layer, for the same reason sign-in does — Credential Manager
     * needs an Activity.
     *
     * Fails with [AuthError.UserMismatch] when the chosen Google account is
     * not the signed-in one. The chooser offers every account on the device,
     * so that is an easy mistake rather than an exotic one.
     */
    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit>

    /**
     * Deletes the Firebase Auth user, ending the account.
     *
     * **Deletes nothing from the database.** The `users/{uid}` subtree is
     * removed separately by [UserProfileRepository.deleteProfile], and it has
     * to go **first**: the rules authorise that write with `auth.uid`, so once
     * this call succeeds there is no longer a session that can make it.
     *
     * Fails with [AuthError.RequiresRecentLogin] unless a re-authentication
     * happened recently enough, which is why both `reauthenticate` calls
     * exist. The session is already gone on success, so there is nothing left
     * to sign out of.
     */
    suspend fun deleteAccount(): Result<Unit>

    fun isLoggedIn(): Boolean
    fun signOut()
}
