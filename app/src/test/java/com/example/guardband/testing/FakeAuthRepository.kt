package com.example.guardband.testing

import com.example.guardband.data.model.GoogleSignInOutcome
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
    var signInWithGoogleResult: Result<GoogleSignInOutcome> = Result.success(RETURNING_GOOGLE_USER),
    private var signedIn: Boolean = false
) : AuthRepository {

    var loginCalls = 0; private set
    var registerCalls = 0; private set
    var sendPasswordResetCalls = 0; private set
    var signInWithGoogleCalls = 0; private set
    var signOutCalls = 0; private set

    /** The token of the last [signInWithGoogle] call, so a test can assert it was forwarded. */
    var lastGoogleIdToken: String? = null; private set

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

    override suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInOutcome> {
        signInWithGoogleCalls++
        lastGoogleIdToken = idToken
        return signInWithGoogleResult
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

        /** A Google sign-in that found an existing account: routes to Home. */
        val RETURNING_GOOGLE_USER = GoogleSignInOutcome(user = DEFAULT_USER, isNewUser = false)

        /** A Google sign-in that just created the account: routes to complete-profile. */
        val NEW_GOOGLE_USER = GoogleSignInOutcome(user = DEFAULT_USER, isNewUser = true)
    }
}
