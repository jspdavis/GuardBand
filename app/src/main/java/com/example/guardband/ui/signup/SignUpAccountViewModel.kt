package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Sign-Up Step 1 — email, password and confirmation.
 *
 * No UiState: the step does no async work, because the account is not created
 * here. It validates the shape of what was typed and hands it to the next
 * step; [SignUpContactsViewModel] still owns `createAccount`, now triggered
 * from the Consent screen.
 *
 * **It cannot tell the user an address is already taken.** The obvious
 * courtesy would be to check availability here rather than at the end, but
 * Firebase's email-enumeration protection exists precisely to stop an app
 * answering "does this address have an account?" for anyone who asks. Login
 * and forgot-password both deliberately refuse to answer it, and a sign-up
 * screen that did would undo that for all three. So a taken address surfaces
 * as [AuthError.EmailAlreadyInUse][com.example.guardband.data.repository.AuthError.EmailAlreadyInUse]
 * at submit, which is late but is the only honest option.
 *
 * Flow: SignUpAccountActivity → SignUpNameActivity
 */
class SignUpAccountViewModel : ViewModel() {

    private val _events = Channel<SignUpAccountEvent>(Channel.BUFFERED)
    val events: Flow<SignUpAccountEvent> = _events.receiveAsFlow()

    /**
     * Validates the three fields and forwards them.
     *
     * [email] is trimmed before it travels, matching what the repository does
     * with it. The passwords are passed through **untrimmed**: a leading or
     * trailing space is a legitimate part of a password, and trimming here
     * would mean the account is created with a different one than was typed.
     */
    fun onNextClicked(email: String, password: String, confirmPassword: String) {
        val error = when {
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

        _events.trySend(SignUpAccountEvent.NavigateToName(email.trim(), password))
    }

    companion object {
        const val MSG_EMAIL_REQUIRED = "Please enter your email address."
        const val MSG_EMAIL_INVALID = "That email address doesn't look right."
        const val MSG_PASSWORD_REQUIRED = "Please choose a password."
        const val MSG_PASSWORD_TOO_SHORT = "Use at least 8 characters."
        const val MSG_PASSWORDS_DIFFER = "Those passwords don't match."
    }
}
