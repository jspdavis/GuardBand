package com.example.guardband.data.repository

import com.example.guardband.data.model.GoogleSignInOutcome
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.model.User
import kotlinx.coroutines.delay

/**
 * [AuthRepository] backed by [InMemoryStore].
 *
 * Kept as the test double for the Firebase implementation — nothing in the app
 * wires it any more (see
 * [RepositoryProvider][com.example.guardband.data.RepositoryProvider]). It
 * returns the same [AuthError]s as [FirebaseAuthRepository] so the two are
 * interchangeable, and its session still dies with the process.
 */
class InMemoryAuthRepository : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        val match = InMemoryStore.findByCredentials(email, password)
            ?: return Result.failure(AuthError.InvalidCredentials)
        InMemoryStore.setSession(match.id)
        return Result.success(match)
    }

    override suspend fun createAccount(
        name: String,
        email: String,
        password: String
    ): Result<User> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        if (InMemoryStore.emailExists(email)) {
            return Result.failure(AuthError.EmailAlreadyInUse)
        }
        val newUser = InMemoryStore.addUser(
            User(name = name.trim(), email = email.trim()),
            password
        )
        InMemoryStore.setSession(newUser.id)
        return Result.success(newUser)
    }

    /**
     * Not supported: there is no Google to talk to without Firebase, and no
     * useful way to fake an ID token exchange here.
     *
     * Returns [AuthError.Unknown] rather than throwing, so a caller that wires
     * this double in degrades to "could not sign in" instead of crashing. The
     * Google path is covered by
     * [FakeAuthRepository][com.example.guardband.testing.FakeAuthRepository],
     * which lets a test name the exact outcome it wants.
     */
    override suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInOutcome> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return Result.failure(AuthError.Unknown(null))
    }

    /**
     * Fails with [AuthError.NoSuchUser] for an unknown email, like Firebase
     * with email-enumeration protection off. Callers are required to treat that
     * as success, so this is the case that exercises the rule.
     */
    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return if (InMemoryStore.emailExists(email)) {
            Result.success(Unit)
        } else {
            Result.failure(AuthError.NoSuchUser)
        }
    }

    /**
     * Not supported: there is no email, so there is no out-of-band code to
     * check. Returns [AuthError.InvalidActionCode], which is what a caller
     * would make of a code this double could never have issued.
     */
    override suspend fun verifyPasswordResetCode(code: String): Result<String> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return Result.failure(AuthError.InvalidActionCode)
    }

    /** Not supported, for the same reason as [verifyPasswordResetCode]. */
    override suspend fun confirmPasswordReset(
        code: String,
        newPassword: String
    ): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return Result.failure(AuthError.InvalidActionCode)
    }

    override fun currentUser(): User? = InMemoryStore.currentUser()

    /**
     * Always [SignInProvider.PASSWORD] for a live session: this double only
     * knows how to make password accounts, and [signInWithGoogle] is not
     * supported.
     */
    override fun currentUserProviders(): Set<SignInProvider> =
        if (currentUser() == null) emptySet() else setOf(SignInProvider.PASSWORD)

    override suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        val user = currentUser() ?: return Result.failure(AuthError.NotSignedIn)
        return if (InMemoryStore.passwordMatches(user.id, password)) {
            Result.success(Unit)
        } else {
            Result.failure(AuthError.InvalidCredentials)
        }
    }

    /** Not supported, for the same reason [signInWithGoogle] is not. */
    override suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return Result.failure(AuthError.Unknown(null))
    }

    /**
     * Removes the user and the session, so the double ends in the same state
     * Firebase leaves behind.
     *
     * It does **not** check for a recent login: there is no token to go stale,
     * so [AuthError.RequiresRecentLogin] is a Firebase-only outcome and a test
     * that wants it uses
     * [FakeAuthRepository][com.example.guardband.testing.FakeAuthRepository].
     */
    override suspend fun deleteAccount(): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        val user = currentUser() ?: return Result.failure(AuthError.NotSignedIn)
        InMemoryStore.removeUser(user.id)
        return Result.success(Unit)
    }

    override fun isLoggedIn(): Boolean = currentUser() != null

    override fun signOut() = InMemoryStore.clearSession()
}
