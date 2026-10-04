package com.example.guardband.data.repository

import com.example.guardband.data.model.User

/**
 * Authentication, password reset and session.
 *
 * Failures are returned as [Result.failure] whose exception message is the
 * user-facing error text. Suspend functions must be called from the main
 * thread (e.g. viewModelScope); implementations switch threads themselves
 * if they need to.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(name: String, location: String, email: String, password: String): Result<User>
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun verifyResetCode(email: String, code: String): Result<Unit>
    suspend fun resetPassword(email: String, newPassword: String): Result<Unit>
    fun currentUser(): User?
    fun isLoggedIn(): Boolean
    fun signOut()
}
