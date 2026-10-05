package com.example.guardband.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * [FirebaseAuthErrorMapper] itself is pure and needs no Android, but
 * constructing any [com.google.firebase.FirebaseException] to feed it does:
 * its constructor runs `Preconditions.checkNotEmpty`, which calls
 * `android.text.TextUtils.isEmpty`. Hence Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class FirebaseAuthErrorMapperTest {

    @Test
    fun `wrong password and invalid credential both mean invalid credentials`() {
        assertEquals(
            AuthError.InvalidCredentials,
            FirebaseAuthErrorMapper.fromCode("ERROR_WRONG_PASSWORD")
        )
        assertEquals(
            AuthError.InvalidCredentials,
            FirebaseAuthErrorMapper.fromCode("ERROR_INVALID_CREDENTIAL")
        )
    }

    @Test
    fun `a provider collision is its own error, not EmailAlreadyInUse`() {
        assertEquals(
            AuthError.AccountExistsWithDifferentCredential,
            FirebaseAuthErrorMapper.fromCode("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL")
        )
        // The two must stay distinct: Google sign-in words them differently.
        assertEquals(
            AuthError.EmailAlreadyInUse,
            FirebaseAuthErrorMapper.fromCode("ERROR_EMAIL_ALREADY_IN_USE")
        )
    }

    @Test
    fun `maps the remaining documented codes`() {
        assertEquals(AuthError.NoSuchUser, FirebaseAuthErrorMapper.fromCode("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthError.UserDisabled, FirebaseAuthErrorMapper.fromCode("ERROR_USER_DISABLED"))
        assertEquals(
            AuthError.EmailAlreadyInUse,
            FirebaseAuthErrorMapper.fromCode("ERROR_EMAIL_ALREADY_IN_USE")
        )
        assertEquals(AuthError.WeakPassword, FirebaseAuthErrorMapper.fromCode("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthError.InvalidEmail, FirebaseAuthErrorMapper.fromCode("ERROR_INVALID_EMAIL"))
        assertEquals(
            AuthError.TooManyRequests,
            FirebaseAuthErrorMapper.fromCode("ERROR_TOO_MANY_REQUESTS")
        )
        assertEquals(
            AuthError.Network,
            FirebaseAuthErrorMapper.fromCode("ERROR_NETWORK_REQUEST_FAILED")
        )
    }

    @Test
    fun `an unrecognised code is kept on Unknown`() {
        assertEquals(
            AuthError.Unknown("ERROR_SOMETHING_NEW"),
            FirebaseAuthErrorMapper.fromCode("ERROR_SOMETHING_NEW")
        )
    }

    @Test
    fun `an AuthError passes through unchanged`() {
        assertEquals(
            AuthError.NotSignedIn,
            FirebaseAuthErrorMapper.map(AuthError.NotSignedIn)
        )
    }

    @Test
    fun `a Firebase network failure becomes Network`() {
        assertEquals(
            AuthError.Network,
            FirebaseAuthErrorMapper.map(FirebaseNetworkException("offline"))
        )
    }

    @Test
    fun `a Firebase rate-limit failure becomes TooManyRequests`() {
        assertEquals(
            AuthError.TooManyRequests,
            FirebaseAuthErrorMapper.map(FirebaseTooManyRequestsException("slow down"))
        )
    }

    @Test
    fun `any other throwable becomes Unknown with no code`() {
        assertEquals(
            AuthError.Unknown(null),
            FirebaseAuthErrorMapper.map(IOException("boom"))
        )
    }

    @Test
    fun `the error carries no message, so a screen cannot leak it`() {
        assertEquals(null, AuthError.InvalidCredentials.message)
        assertEquals(null, AuthError.Unknown("ERROR_X").message)
    }
}
