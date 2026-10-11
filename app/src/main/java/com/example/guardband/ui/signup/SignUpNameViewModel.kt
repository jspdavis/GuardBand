package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Sign-Up Step 2 — the user's first and last name.
 *
 * No UiState: the step has no async work.
 *
 * **Both names are required, and they are stored joined.** The account's
 * display name and `users/{uid}/name` are a single field, so the two inputs
 * are a presentation choice rather than a data one — [fullName] is the only
 * place that decides how they combine, so nothing downstream has to guess.
 *
 * The credentials from step 1 pass through untouched; this screen does not
 * read or re-validate them.
 *
 * Flow: SignUpNameActivity → SignUpContactsActivity
 */
class SignUpNameViewModel : ViewModel() {

    private val _events = Channel<SignUpNameEvent>(Channel.BUFFERED)
    val events: Flow<SignUpNameEvent> = _events.receiveAsFlow()

    fun onNextClicked(
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ) {
        val error = when {
            InputValidator.isBlank(firstName) -> MSG_FIRST_NAME_REQUIRED
            InputValidator.isBlank(lastName) -> MSG_LAST_NAME_REQUIRED
            !InputValidator.isValidContactName(fullName(firstName, lastName)) -> MSG_NAME_TOO_LONG
            else -> null
        }

        if (error != null) {
            _events.trySend(SignUpNameEvent.ShowMessage(error))
            return
        }

        _events.trySend(
            SignUpNameEvent.NavigateToContacts(
                name = fullName(firstName, lastName),
                email = email,
                password = password
            )
        )
    }

    /** "Continue with Google" on the sign-up entry screen. */
    fun onGoogleSignInClicked() {
        _events.trySend(SignUpNameEvent.NavigateToGoogleSignIn)
    }

    companion object {
        /**
         * The stored form: both parts trimmed, joined by a single space.
         *
         * Reused by the length check so the rule applies to what is actually
         * written, not to either half.
         */
        fun fullName(firstName: String, lastName: String): String =
            "${firstName.trim()} ${lastName.trim()}".trim()

        const val MSG_FIRST_NAME_REQUIRED = "Please enter your first name."
        const val MSG_LAST_NAME_REQUIRED = "Please enter your last name."
        const val MSG_NAME_TOO_LONG = "That name is too long."
    }
}
