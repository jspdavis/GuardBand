package com.example.guardband.data.repository

import com.example.guardband.data.model.GoogleSignInOutcome
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

    override suspend fun register(
        name: String,
        location: String,
        email: String,
        password: String
    ): Result<User> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        if (InMemoryStore.emailExists(email)) {
            return Result.failure(AuthError.EmailAlreadyInUse)
        }
        val newUser = InMemoryStore.addUser(
            User(name = name.trim(), email = email.trim(), location = location.trim()),
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

    override fun currentUser(): User? = InMemoryStore.currentUser()

    override fun isLoggedIn(): Boolean = currentUser() != null

    override fun signOut() = InMemoryStore.clearSession()
}
