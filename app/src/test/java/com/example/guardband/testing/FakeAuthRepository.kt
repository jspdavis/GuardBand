package com.example.guardband.testing

import com.example.guardband.data.model.User
import com.example.guardband.data.repository.AuthRepository

/**
 * [AuthRepository] whose every call returns whatever the test puts in it.
 *
 * Preferred over [com.example.guardband.data.repository.InMemoryAuthRepository]
 * for ViewModel tests: that one has a 1.2 s delay and its own rules, while this
 * one lets a test name the exact [com.example.guardband.data.repository.AuthError]
 * it wants mapped.
 */
class FakeAuthRepository(
    var loginResult: Result<User> = Result.success(DEFAULT_USER),
    var registerResult: Result<User> = Result.success(DEFAULT_USER),
    var sendPasswordResetResult: Result<Unit> = Result.success(Unit),
    private var signedIn: Boolean = false
) : AuthRepository {

    var loginCalls = 0; private set
    var registerCalls = 0; private set
    var sendPasswordResetCalls = 0; private set
    var signOutCalls = 0; private set

    /** Arguments of the last [register] call, so a test can assert what was forwarded. */
    var lastRegisterArgs: List<String> = emptyList(); private set

    override suspend fun login(email: String, password: String): Result<User> {
        loginCalls++
        return loginResult
    }

    override suspend fun register(
        name: String,
        location: String,
        email: String,
        password: String
    ): Result<User> {
        registerCalls++
        lastRegisterArgs = listOf(name, location, email, password)
        return registerResult
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        sendPasswordResetCalls++
        return sendPasswordResetResult
    }

    override fun currentUser(): User? = DEFAULT_USER.takeIf { signedIn }

    override fun isLoggedIn(): Boolean = signedIn

    override fun signOut() {
        signOutCalls++
        signedIn = false
    }

    companion object {
        val DEFAULT_USER = User(
            id = "uid-1",
            name = "Test User",
            email = "test@guardband.com",
            location = ""
        )
    }
}
