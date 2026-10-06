package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sign-Up Step 3 — account credentials, the emergency contact, and final
 * registration.
 *
 * Runs in two modes:
 *  - **register** (email sign-up): validates the credentials, creates the
 *    account with [AuthRepository.createAccount], then writes the profile and
 *    the contact with [UserProfileRepository.finalizeSignUp].
 *  - **complete profile** (first-time Google sign-in): the account already
 *    exists, so the credential fields are hidden and only the finalize step
 *    runs. No account is created.
 *
 * **Registration is two steps, and the retry re-runs only the second.** The
 * account is created first and the database write follows, because the write is
 * the part that can fail with the account already made. When those were one
 * call, that failure left the address unable to finish registering at all: the
 * only retry available went back through account creation, which then failed as
 * "email already in use". Now [onSubmitClicked] checks
 * [SignUpContactsUiState.accountCreated] before it creates anything, so tapping
 * Retry resumes at the write however many times it takes.
 *
 * [setCompleteProfileMode] must be called before the first submit; the
 * Activity does that from its Intent extras.
 *
 * Flow: SignUpContactsActivity → LoadingActivity → HomeActivity
 */
class SignUpContactsViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpContactsUiState())
    val uiState: StateFlow<SignUpContactsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignUpContactsEvent>(Channel.BUFFERED)
    val events: Flow<SignUpContactsEvent> = _events.receiveAsFlow()

    /**
     * Submit, and also Retry: the Activity calls this with the same arguments
     * either way, and the [SignUpContactsUiState.accountCreated] guard below is
     * what makes the second call resume instead of start over.
     *
     * [name] comes from the previous step. The credentials are passed
     * to the repository untrimmed, as before; the contact fields are trimmed and
     * normalised.
     */
    fun onSubmitClicked(
        name: String,
        email: String,
        password: String,
        contactName: String,
        contactPhone: String,
        contactRelationship: String
    ) {
        if (_uiState.value.isLoading) return

        // Validated before anything is created, so a mistyped phone number can
        // never be the reason an already-made account cannot be finished.
        val contacts = validatedContacts(contactName, contactPhone, contactRelationship)
            ?: return

        if (_uiState.value.completeProfile) {
            val user = authRepository.currentUser()
            if (user == null) {
                _events.trySend(SignUpContactsEvent.ShowMessage(MSG_SESSION_EXPIRED))
                return
            }
            finalize(
                uid = user.id,
                name = name.trim().ifEmpty { user.name },
                email = user.email,
                contacts = contacts
            )
            return
        }

        if (_uiState.value.accountCreated) {
            // A previous attempt got the account made and the write failed.
            // Resuming at the write is the whole point of the split.
            retryFinalize(name, contacts)
            return
        }

        if (!validateCredentials(email, password)) return

        _uiState.update { it.copy(isLoading = true, canRetry = false) }
        viewModelScope.launch {
            authRepository.createAccount(name, email, password)
                .onSuccess { user ->
                    _uiState.update { it.copy(accountCreated = true) }
                    finalizeNow(
                        uid = user.id,
                        // Same expression as retryFinalize, so the first
                        // attempt and every retry write the identical record.
                        name = name.trim().ifEmpty { user.name },
                        email = user.email,
                        contacts = contacts
                    )
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(SignUpContactsEvent.ShowMessage(messageFor(error)))
                }
        }
    }

    /** Switches the screen into complete-profile mode. Called once, before submit. */
    fun setCompleteProfileMode(completeProfile: Boolean) {
        _uiState.update { it.copy(completeProfile = completeProfile) }
    }

    // ── Finalize ──────────────────────────────────────────────────────────────

    /**
     * Re-runs the write for an account that already exists.
     *
     * The uid comes from the live session, not from a field held since the
     * first attempt: the session is what `createAccount` left behind, and it is
     * the same reasoning complete-profile mode uses. No session means something
     * signed the user out between attempts, which is reported rather than
     * written past.
     */
    private fun retryFinalize(name: String, contacts: List<EmergencyContact>) {
        val user = authRepository.currentUser()
        if (user == null) {
            _events.trySend(SignUpContactsEvent.ShowMessage(MSG_SESSION_EXPIRED))
            return
        }
        finalize(
            uid = user.id,
            name = name.trim().ifEmpty { user.name },
            email = user.email,
            contacts = contacts
        )
    }

    private fun finalize(
        uid: String,
        name: String,
        email: String,
        contacts: List<EmergencyContact>
    ) {
        _uiState.update { it.copy(isLoading = true, canRetry = false) }
        viewModelScope.launch {
            finalizeNow(uid, name, email, contacts)
        }
    }

    /**
     * The one atomic write (D5): profile and contacts together, so there is no
     * partial state for a retry to reconcile.
     *
     * Already inside a coroutine, and assumes `isLoading` is set.
     */
    private suspend fun finalizeNow(
        uid: String,
        name: String,
        email: String,
        contacts: List<EmergencyContact>
    ) {
        userProfileRepository.finalizeSignUp(
            uid = uid,
            name = name.trim(),
            email = email,
            contacts = contacts
        )
            .onSuccess {
                _uiState.update { it.copy(isLoading = false, canRetry = false) }
                _events.send(SignUpContactsEvent.NavigateToLoadingHome)
            }
            .onFailure { error ->
                // canRetry, not a dead end: the account exists either way, and
                // in complete-profile mode so does the session.
                _uiState.update { it.copy(isLoading = false, canRetry = true) }
                _events.send(SignUpContactsEvent.ShowMessage(finalizeMessageFor(error)))
            }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * The contacts to store, or null when a message was emitted instead.
     *
     * An entirely blank block is no contact rather than an error: the Contacts
     * tab is where the user is asked to reach
     * [MIN_CONTACTS][InputValidator.MIN_CONTACTS], and this screen has room for
     * one. A half-filled block is an error, because silently dropping what
     * someone typed is worse than telling them.
     */
    private fun validatedContacts(
        name: String,
        phone: String,
        relationship: String
    ): List<EmergencyContact>? {
        if (InputValidator.isBlank(name) && InputValidator.isBlank(phone)) {
            return emptyList()
        }

        val error = when {
            !InputValidator.isValidContactName(name) -> MSG_CONTACT_NAME_INVALID
            !InputValidator.isValidPhone(phone) -> MSG_CONTACT_PHONE_INVALID
            !InputValidator.isValidContactRelationship(relationship) ->
                MSG_CONTACT_RELATIONSHIP_INVALID
            else -> null
        }
        if (error != null) {
            _events.trySend(SignUpContactsEvent.ShowMessage(error))
            return null
        }

        return listOf(
            EmergencyContact(
                name = name.trim(),
                // Left as typed: the repository normalises to E.164, and it is
                // the only layer that gets to decide the stored form.
                phone = phone.trim(),
                relationship = relationship.trim()
            )
        )
    }

    /** Emits the first failing rule's message and returns false. */
    private fun validateCredentials(email: String, password: String): Boolean {
        val error = when {
            InputValidator.isBlank(email) || !InputValidator.isValidEmail(email) -> MSG_EMAIL_INVALID
            InputValidator.isBlank(password) -> MSG_PASSWORD_REQUIRED
            !InputValidator.isPasswordLongEnough(password) -> MSG_PASSWORD_TOO_SHORT
            else -> return true
        }
        _events.trySend(SignUpContactsEvent.ShowMessage(error))
        return false
    }

    // ── Errors ────────────────────────────────────────────────────────────────

    /** Wording for a failure while creating the account. Never shows the exception text. */
    private fun messageFor(error: Throwable): String = when (error) {
        // Both mean "pick another address": one is taken by a password account,
        // the other by a Google one. Sign-up does not need to tell them apart,
        // and saying which provider owns it would leak who has an account.
        AuthError.EmailAlreadyInUse,
        AuthError.AccountExistsWithDifferentCredential -> MSG_EMAIL_IN_USE

        AuthError.InvalidEmail -> MSG_EMAIL_INVALID

        // Only reachable if Firebase's rule is ever stricter than ours.
        AuthError.WeakPassword -> MSG_PASSWORD_TOO_SHORT

        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        else -> MSG_SIGN_UP_FAILED
    }

    /**
     * Wording for a failure in the write, which reads differently: the account
     * is already made, so the message says what is left rather than that
     * sign-up failed.
     */
    private fun finalizeMessageFor(error: Throwable): String = when (error) {
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.PermissionDenied -> MSG_SAVE_REFUSED
        AuthError.NotSignedIn -> MSG_SESSION_EXPIRED
        else -> MSG_SAVE_FAILED
    }

    companion object {
        const val MSG_EMAIL_INVALID = "Enter a valid email address."
        const val MSG_PASSWORD_REQUIRED = "Password is required."
        const val MSG_PASSWORD_TOO_SHORT = "Password must be at least 8 characters."
        const val MSG_EMAIL_IN_USE = "An account with that email already exists."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_SIGN_UP_FAILED = "Could not create your account. Please try again."
        const val MSG_SESSION_EXPIRED = "You are no longer signed in. Please sign in again."

        const val MSG_CONTACT_NAME_INVALID = "Enter the contact's name."
        const val MSG_CONTACT_PHONE_INVALID =
            "Enter a valid mobile number, like 09171234567."
        const val MSG_CONTACT_RELATIONSHIP_INVALID = "That relationship is too long."

        /** The account exists, so Retry is the action — not starting over. */
        const val MSG_SAVE_FAILED = "Your account is ready, but we couldn't save your details. Tap Retry."
        const val MSG_SAVE_REFUSED = "Your account is ready, but saving your details was refused. Tap Retry."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SignUpContactsViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
