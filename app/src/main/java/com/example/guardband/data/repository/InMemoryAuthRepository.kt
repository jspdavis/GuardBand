package com.example.guardband.data.repository

import com.example.guardband.data.model.User
import kotlinx.coroutines.delay

/**
 * [AuthRepository] backed by [InMemoryStore].
 *
 * Behaviour and error messages match MockRepository exactly (parity with the
 * MVP screens); only the calling convention differs.
 */
class InMemoryAuthRepository : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        val match = InMemoryStore.findByCredentials(email, password)
            ?: return Result.failure(Exception("Invalid email or password."))
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
            return Result.failure(Exception("An account with that email already exists."))
        }
        val newUser = InMemoryStore.addUser(
            User(name = name, email = email, location = location),
            password
        )
        InMemoryStore.setSession(newUser.id)
        return Result.success(newUser)
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        if (!InMemoryStore.emailExists(email)) {
            return Result.failure(Exception("No account found for that email."))
        }
        InMemoryStore.pendingResetCode = "123456" // fixed mock code
        return Result.success(Unit)
    }

    /** [email] is unused for parity: the mock checks only the fixed code. */
    override suspend fun verifyResetCode(email: String, code: String): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return if (code.trim() == InMemoryStore.pendingResetCode) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Incorrect verification code. Try again."))
        }
    }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        return if (InMemoryStore.updatePassword(email, newPassword)) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Session expired. Please restart the reset flow."))
        }
    }

    override fun currentUser(): User? = InMemoryStore.currentUser()

    override fun isLoggedIn(): Boolean = currentUser() != null

    override fun signOut() = InMemoryStore.clearSession()
}
