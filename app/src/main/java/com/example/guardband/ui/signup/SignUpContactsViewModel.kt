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
import com.example.guardband.data.repository.ContactRepository
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
 *  - **register** (email sign-up): validates the credentials and calls
 *    [AuthRepository.register], which creates the account and the profile.
 *  - **complete profile** (first-time Google sign-in): the account already
 *    exists, so the credential fields are hidden and
 *    [UserProfileRepository.saveProfile] finishes the `users/{uid}` record
 *    with the location the user just entered. No account is created.
 *
 * [setCompleteProfileMode] must be called before the first submit; the
 * Activity does that from its Intent extras.
 *
 * Flow: SignUpContactsActivity → LoadingActivity → HomeActivity
 */
class SignUpContactsViewModel(
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpContactsUiState())
    val uiState: StateFlow<SignUpContactsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignUpContactsEvent>(Channel.BUFFERED)
    val events: Flow<SignUpContactsEvent> = _events.receiveAsFlow()

    /**
     * [name]/[location] come from the previous steps; every value is passed to
     * the repository untrimmed, as before. The contact fields are trimmed and
     * saved only when both name and phone are filled in.
     */
    fun onSubmitClicked(
        name: String,
        location: String,
        email: String,
        password: String,
        contactName: String,
        contactPhone: String,
        contactRelationship: String
    ) {
        if (_uiState.value.isLoading) return

        if (_uiState.value.completeProfile) {
            completeProfile(name, location, contactName, contactPhone, contactRelationship)
            return
        }

        if (!validateCredentials(email, password)) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.register(name, location, email, password)
                .onSuccess {
                    saveContactIfPresent(contactName, contactPhone, contactRelationship)
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(SignUpContactsEvent.NavigateToLoadingHome)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(SignUpContactsEvent.ShowMessage(messageFor(error)))
                }
        }
    }

    /**
     * Finishes the profile of an account a Google sign-in already created.
     *
     * The uid comes from the live session rather than an Intent extra, so a
     * stale extra can never write to the wrong record. No session means
     * something signed the user out between screens, which is reported rather
     * than written past.
     */
    private fun completeProfile(
        name: String,
        location: String,
        contactName: String,
        contactPhone: String,
        contactRelationship: String
    ) {
        val user = authRepository.currentUser()
        if (user == null) {
            _events.trySend(SignUpContactsEvent.ShowMessage(MSG_SESSION_EXPIRED))
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            userProfileRepository.saveProfile(
                uid = user.id,
                name = name.trim().ifEmpty { user.name },
                email = user.email,
                location = location.trim()
            )
                .onSuccess {
                    saveContactIfPresent(contactName, contactPhone, contactRelationship)
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(SignUpContactsEvent.NavigateToLoadingHome)
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

    /**
     * Saves the sign-up contact (previously discarded). Skipped silently when
     * name or phone is blank; a failure is ignored because the account already
     * exists. The id is assigned by the repository.
     */
    private suspend fun saveContactIfPresent(name: String, phone: String, relationship: String) {
        if (InputValidator.isBlank(name) || InputValidator.isBlank(phone)) return
        contactRepository.addContact(
            EmergencyContact(
                name = name.trim(),
                phone = phone.trim(),
                relationship = relationship.trim()
            )
        )
    }

    // ── Validation ────────────────────────────────────────────────────────────

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

    /** Wording for an [AuthError]. Never shows the exception text. */
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

    companion object {
        const val MSG_EMAIL_INVALID = "Enter a valid email address."
        const val MSG_PASSWORD_REQUIRED = "Password is required."
        const val MSG_PASSWORD_TOO_SHORT = "Password must be at least 8 characters."
        const val MSG_EMAIL_IN_USE = "An account with that email already exists."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_SIGN_UP_FAILED = "Could not create your account. Please try again."
        const val MSG_SESSION_EXPIRED = "You are no longer signed in. Please sign in again."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SignUpContactsViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.contactRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
