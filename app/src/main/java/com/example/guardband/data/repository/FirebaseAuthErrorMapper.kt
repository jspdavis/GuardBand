package com.example.guardband.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/**
 * Turns the exceptions Firebase throws into [AuthError].
 *
 * Maps on `FirebaseAuthException.errorCode`, never on the exception message:
 * messages are localised and change between SDK versions, so matching them is
 * the bug the MVP branch shipped.
 *
 * Pure and Android-free, so [fromCode] is unit-testable without a device.
 */
internal object FirebaseAuthErrorMapper {

    /** Maps any throwable from a Firebase call. [AuthError]s pass straight through. */
    fun map(throwable: Throwable): AuthError = when (throwable) {
        is AuthError -> throwable
        is FirebaseAuthException -> fromCode(throwable.errorCode)
        is FirebaseNetworkException -> AuthError.Network
        is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
        else -> AuthError.Unknown(null)
    }

    /** Maps one `FirebaseAuthException.errorCode`. Unrecognised codes become [AuthError.Unknown]. */
    fun fromCode(errorCode: String): AuthError = when (errorCode) {
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_WRONG_PASSWORD" -> AuthError.InvalidCredentials

        "ERROR_USER_NOT_FOUND" -> AuthError.NoSuchUser
        "ERROR_USER_DISABLED" -> AuthError.UserDisabled

        "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailAlreadyInUse

        // Its own error, not EmailAlreadyInUse: it means "that address signs in
        // another way", which reads differently from "that address is taken".
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            AuthError.AccountExistsWithDifferentCredential

        "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
        "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
        "ERROR_TOO_MANY_REQUESTS" -> AuthError.TooManyRequests
        "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.Network

        else -> AuthError.Unknown(errorCode)
    }
}
