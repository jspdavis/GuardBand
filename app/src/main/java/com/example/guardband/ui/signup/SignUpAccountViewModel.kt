package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Sign-Up Step 1 — own phone, email, password and confirmation.
 *
 * Flow: SignUpAccountActivity → SignUpNameActivity
 */
class SignUpAccountViewModel : ViewModel() {

    private val _events = Channel<SignUpAccountEvent>(Channel.BUFFERED)
    val events: Flow<SignUpAccountEvent> = _events.receiveAsFlow()

    /**
     * Validates the fields and forwards them.
     */
    fun onNextClicked(
        email: String,
        password: String,
        confirmPassword: String,
        phone: String = ""
    ) {
        val error = when {
            phone.isNotBlank() && InputValidator.normalizePhoneToE164(phone) == null -> MSG_PHONE_INVALID
            InputValidator.isBlank(email) -> MSG_EMAIL_REQUIRED
            !InputValidator.isValidEmail(email.trim()) -> MSG_EMAIL_INVALID
            InputValidator.isBlank(password) -> MSG_PASSWORD_REQUIRED
            !InputValidator.isPasswordLongEnough(password) -> MSG_PASSWORD_TOO_SHORT
            !InputValidator.passwordsMatch(password, confirmPassword) -> MSG_PASSWORDS_DIFFER
            else -> null
        }

        if (error != null) {
            _events.trySend(SignUpAccountEvent.ShowMessage(error))
            return
        }

        val normalizedPhone = if (phone.isNotBlank()) {
            InputValidator.normalizePhoneToE164(phone) ?: phone.trim()
        } else {
            ""
        }

        _events.trySend(
            SignUpAccountEvent.NavigateToName(
                email = email.trim(),
                password = password,
                ownPhone = normalizedPhone
            )
        )
    }

    companion object {
        const val MSG_PHONE_INVALID = "Please enter a valid phone number (e.g. +639XXXXXXXXX)."
        const val MSG_EMAIL_REQUIRED = "Please enter your email address."
        const val MSG_EMAIL_INVALID = "That email address doesn't look right."
        const val MSG_PASSWORD_REQUIRED = "Please choose a password."
        const val MSG_PASSWORD_TOO_SHORT = "Use at least 8 characters."
        const val MSG_PASSWORDS_DIFFER = "Those passwords don't match."
    }
}
