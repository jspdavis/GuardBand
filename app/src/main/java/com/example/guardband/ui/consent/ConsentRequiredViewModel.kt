package com.example.guardband.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import com.example.guardband.utils.HostedPages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The consent step for an account that reached the app without one - a
 * Console-made account, or a Google account whose record went missing. The
 * Home gate sends them here ([com.example.guardband.ui.home.HomeEvent.RequireConsent]).
 *
 * **The same agreement as sign-up.** Same statement, same checkbox, same link
 * to the notice (the layout is sign-up's own), and Agree writes the same
 * three fields through [UserProfileRepository.recordConsent] - which shares
 * its code with [UserProfileRepository.finalizeSignUp] - at the same
 * [UserProfileRepository.CONSENT_VERSION]. A consent record therefore means
 * the same thing whichever route made it.
 *
 * **Agree never continues without a stored record.** A failed write keeps the
 * screen, says so, and offers Retry. The uid is the **live session's**, never
 * an extra.
 *
 * **Decline signs out and changes nothing else.** The account, its profile and
 * its contacts are left exactly as they were and nothing is written to
 * `consent`; the user lands on Login with a short explanation, and meets this
 * screen again on their next sign-in. This screen offers no in-app way to
 * delete the account, since Settings is behind the gate; the hosted
 * `/delete-account` page describes the manual route, by email.
 *
 * Flow: Home gate → ConsentRequiredActivity → (Agree) HomeActivity
 *       ConsentRequiredActivity → (Decline) LoginActivity
 */
class ConsentRequiredViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsentRequiredUiState())
    val uiState: StateFlow<ConsentRequiredUiState> = _uiState.asStateFlow()

    private val _events = Channel<ConsentRequiredEvent>(Channel.BUFFERED)
    val events: Flow<ConsentRequiredEvent> = _events.receiveAsFlow()

    fun onConsentChanged(given: Boolean) {
        _uiState.update { it.copy(consentGiven = given) }
    }

    /** Readable before agreeing, which is the point of the link. */
    fun onPrivacyNoticeClicked() {
        _events.trySend(ConsentRequiredEvent.OpenPrivacyNotice(HostedPages.privacyNotice))
    }

    fun onPrivacyNoticeUnavailable() {
        _events.trySend(ConsentRequiredEvent.ShowMessage(MSG_NO_BROWSER))
    }

    /** Agree, and also Retry after a failed write: the same call either way. */
    fun onAgreeClicked() {
        val state = _uiState.value
        if (state.isSaving) return

        // The button is disabled without it, but consent is the one thing
        // that must never be assumed from the UI alone.
        if (!state.consentGiven) {
            _events.trySend(ConsentRequiredEvent.ShowMessage(MSG_CONSENT_REQUIRED))
            return
        }

        val user = authRepository.currentUser()
        if (user == null) {
            _events.trySend(ConsentRequiredEvent.ShowMessage(MSG_SESSION_EXPIRED))
            _events.trySend(ConsentRequiredEvent.NavigateToLogin)
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Null is the timeout. An RTDB write made offline does not fail, it
            // queues, so without this the spinner would never end and Decline
            // would stay disabled. If the queued write lands later, the user
            // did agree; the gate will simply find the record next time.
            (withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                userProfileRepository.recordConsent(user.id, UserProfileRepository.CONSENT_VERSION)
            } ?: Result.failure(AuthError.Network))
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveFailed = false) }
                    _events.send(ConsentRequiredEvent.ContinueToHome)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                    _events.send(ConsentRequiredEvent.ShowMessage(messageFor(error)))
                }
        }
    }

    /**
     * Signs out and writes nothing. Ignored mid-write, so a decline cannot
     * race an agreement that is already on its way.
     */
    fun onDeclineClicked() {
        if (_uiState.value.isSaving) return
        authRepository.signOut()
        _events.trySend(ConsentRequiredEvent.Declined(MSG_DECLINED))
    }

    /** Wording for a failed write. Never shows the exception text. */
    private fun messageFor(error: Throwable): String = when (error) {
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.PermissionDenied -> MSG_SAVE_REFUSED
        AuthError.NotSignedIn -> MSG_SESSION_EXPIRED
        else -> MSG_SAVE_FAILED
    }

    companion object {
        /** How long Agree waits for the write before offering Try again. */
        const val WRITE_TIMEOUT_MS = 10_000L

        const val MSG_CONSENT_REQUIRED = "Please tick the box to agree first."
        const val MSG_NO_BROWSER = "No app on this phone can open the Privacy Notice."
        const val MSG_NO_CONNECTION = "No connection. Check your network and tap Try again."
        const val MSG_SAVE_FAILED = "We couldn't save your agreement. Tap Try again."
        const val MSG_SAVE_REFUSED = "Saving your agreement was refused. Tap Try again."
        const val MSG_SESSION_EXPIRED = "You are no longer signed in. Please sign in again."
        const val MSG_DECLINED =
            "GuardBand can't be used without your agreement. You've been signed out; your account hasn't changed."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConsentRequiredViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
