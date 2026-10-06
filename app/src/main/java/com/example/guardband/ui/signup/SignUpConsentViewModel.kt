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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sign-Up final screen — consent, and the commit.
 *
 * Every earlier step only collected; this is the one that writes. Running it
 * last is what makes an abandoned sign-up leave nothing behind, and it is why
 * a taken email address is not discovered until here.
 *
 * Runs in two modes:
 *  - **register** (email sign-up): creates the account with
 *    [AuthRepository.createAccount], then writes the profile, the contacts and
 *    the consent record with [UserProfileRepository.finalizeSignUp].
 *  - **complete profile** (first-time Google sign-in): the account already
 *    exists, so only the write runs.
 *
 * **Registration is two calls, and Retry re-runs only the second.** The write
 * is the part that can fail with the account already made. When those were one
 * call, that failure left the address unable to finish registering at all: the
 * only retry available went back through account creation, which then failed
 * as "email already in use". [onCreateAccountClicked] checks
 * [SignUpConsentUiState.accountCreated] before creating anything, so Retry
 * resumes at the write however many times it takes.
 *
 * **Consent is recorded, not just collected.** A ticked box that leaves no
 * trace is no use as a consent record, so the version and a server timestamp
 * go into the same atomic write as the profile — see
 * [UserProfileRepository.CONSENT_VERSION].
 *
 * Flow: SignUpConsentActivity → LoadingActivity → HomeActivity
 */
class SignUpConsentViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpConsentUiState())
    val uiState: StateFlow<SignUpConsentUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignUpConsentEvent>(Channel.BUFFERED)
    val events: Flow<SignUpConsentEvent> = _events.receiveAsFlow()

    fun onConsentChanged(given: Boolean) {
        _uiState.update { it.copy(consentGiven = given) }
    }

    /**
     * Create account, and also Retry: the Activity calls this with the same
     * arguments either way, and the [SignUpConsentUiState.accountCreated] guard
     * is what makes the second call resume instead of start over.
     *
     * The credentials reach the repository untrimmed, as before.
     */
    fun onCreateAccountClicked(
        name: String,
        email: String,
        password: String,
        contacts: List<EmergencyContact>,
        completeProfile: Boolean
    ) {
        val state = _uiState.value
        if (state.isLoading) return

        // Belt and braces: the button is disabled without it, but consent is
        // the one thing that must never be assumed from the UI alone.
        if (!state.consentGiven) {
            _events.trySend(SignUpConsentEvent.ShowMessage(MSG_CONSENT_REQUIRED))
            return
        }

        if (completeProfile) {
            val user = authRepository.currentUser()
            if (user == null) {
                _events.trySend(SignUpConsentEvent.ShowMessage(MSG_SESSION_EXPIRED))
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

        if (state.accountCreated) {
            // A previous attempt got the account made and the write failed.
            // Resuming at the write is the whole point of the split.
            retryFinalize(name, contacts)
            return
        }

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
                    _events.send(SignUpConsentEvent.ShowMessage(messageFor(error)))
                }
        }
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
            _events.trySend(SignUpConsentEvent.ShowMessage(MSG_SESSION_EXPIRED))
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
     * The one atomic write: profile, contacts and consent together, so there is
     * no partial state for a retry to reconcile.
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
            contacts = contacts,
            consentVersion = UserProfileRepository.CONSENT_VERSION
        )
            .onSuccess {
                _uiState.update { it.copy(isLoading = false, canRetry = false) }
                _events.send(SignUpConsentEvent.NavigateToLoadingHome)
            }
            .onFailure { error ->
                // canRetry, not a dead end: the account exists either way, and
                // in complete-profile mode so does the session.
                _uiState.update { it.copy(isLoading = false, canRetry = true) }
                _events.send(SignUpConsentEvent.ShowMessage(finalizeMessageFor(error)))
            }
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
        const val MSG_CONSENT_REQUIRED = "Please agree before creating your account."
        const val MSG_EMAIL_INVALID = "Enter a valid email address."
        const val MSG_PASSWORD_TOO_SHORT = "Password must be at least 8 characters."
        const val MSG_EMAIL_IN_USE = "An account with that email already exists."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_SIGN_UP_FAILED = "Could not create your account. Please try again."
        const val MSG_SESSION_EXPIRED = "You are no longer signed in. Please sign in again."

        /** The account exists, so Retry is the action — not starting over. */
        const val MSG_SAVE_FAILED =
            "Your account is ready, but we couldn't save your details. Tap Retry."
        const val MSG_SAVE_REFUSED =
            "Your account is ready, but saving your details was refused. Tap Retry."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SignUpConsentViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
