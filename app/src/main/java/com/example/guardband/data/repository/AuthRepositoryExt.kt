package com.example.guardband.data.repository

import com.example.guardband.data.model.User
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspend wrappers around AuthRepository's callback-based methods.
 * These allow ViewModels to use coroutines without modifying the repository.
 */

suspend fun AuthRepository.loginSuspend(identifier: String, password: String): User =
    suspendCancellableCoroutine { cont ->
        login(
            identifier = identifier,
            password = password,
            onSuccess = { user -> if (cont.isActive) cont.resume(user) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.registerWithEmailSuspend(
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    phone: String = ""
): User = suspendCancellableCoroutine { cont ->
    registerWithEmail(
        email = email,
        password = password,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        onSuccess = { user -> if (cont.isActive) cont.resume(user) },
        onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
    )
}

suspend fun AuthRepository.signInWithGoogleIdTokenSuspend(idToken: String): Pair<User, Boolean> =
    suspendCancellableCoroutine { cont ->
        signInWithGoogleIdToken(
            idToken = idToken,
            onSuccess = { user, isNew -> if (cont.isActive) cont.resume(Pair(user, isNew)) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.requestPasswordResetSuspend(email: String): String? =
    suspendCancellableCoroutine { cont ->
        requestPasswordReset(
            email = email,
            onSuccess = { otp -> if (cont.isActive) cont.resume(otp) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.resendOtpSuspend(email: String): String? =
    suspendCancellableCoroutine { cont ->
        resendOtp(
            email = email,
            onSuccess = { otp -> if (cont.isActive) cont.resume(otp) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.verifyOtpSuspend(email: String, code: String): Unit =
    suspendCancellableCoroutine { cont ->
        verifyOtp(
            email = email,
            code = code,
            onSuccess = { if (cont.isActive) cont.resume(Unit) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.updatePasswordWithOtpSuspend(email: String, newPassword: String): Unit =
    suspendCancellableCoroutine { cont ->
        updatePasswordWithOtp(
            email = email,
            newPassword = newPassword,
            onSuccess = { if (cont.isActive) cont.resume(Unit) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.updateProfileSuspend(updates: Map<String, Any?>): User =
    suspendCancellableCoroutine { cont ->
        updateProfile(
            updates = updates,
            onSuccess = { user -> if (cont.isActive) cont.resume(user) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.fetchProfileSuspend(): User =
    suspendCancellableCoroutine { cont ->
        fetchProfile(
            onSuccess = { user -> if (cont.isActive) cont.resume(user) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }

suspend fun AuthRepository.changePasswordSuspend(currentPassword: String, newPassword: String): Unit =
    suspendCancellableCoroutine { cont ->
        changePassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
            onSuccess = { if (cont.isActive) cont.resume(Unit) },
            onError = { msg -> if (cont.isActive) cont.resumeWithException(Exception(msg)) }
        )
    }
