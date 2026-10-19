package com.example.guardband.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.ui.account.EmailVerification
import com.example.guardband.ui.track.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The verification gate: a password account whose email is not verified yet
 * lands here instead of the tabs, and stays until it is.
 *
 * **How it lets them through.** The account is reloaded whenever the screen
 * comes to the front ([onScreenShown]) - typically on the way back from the
 * email app - and on "I've verified my email" ([onCheckClicked]). The moment
 * Firebase says verified, [VerifyRequiredEvent.ContinueToHome]. Reloading
 * never signs anyone out.
 *
 * **Ways out that do not need the inbox:** Resend (60 s cooldown, the same
 * injected-clock pattern as Account information and forgot password), Log
 * Out, and "Wrong email? Delete this account", which opens Account
 * information - the one place Delete account lives, safeguards and all. A
 * mistyped address would otherwise strand the account with no link it could
 * ever receive.
 *
 * Google accounts never reach this screen ([EmailVerification]); one that
 * somehow did is waved straight through.
 *
 * Nothing here logs: not the email, not Firebase's text.
 */
class VerifyRequiredViewModel(
    private val authRepository: AuthRepository,
    private val clock: Clock = Clock.SYSTEM,
    private val cooldownTicks: Flow<Unit> = defaultCooldownTicks()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyRequiredUiState())
    val uiState: StateFlow<VerifyRequiredUiState> = _uiState.asStateFlow()

    private val _events = Channel<VerifyRequiredEvent>(Channel.BUFFERED)
    val events: Flow<VerifyRequiredEvent> = _events.receiveAsFlow()

    /** When Resend unlocks, as a clock reading. */
    private var resendAvailableAt: Long = 0

    init {
        val user = authRepository.currentUser()
        when {
            user == null -> _events.trySend(VerifyRequiredEvent.NavigateToLogin)
            !isStillRequired() -> _events.trySend(VerifyRequiredEvent.ContinueToHome)
            else -> {
                _uiState.update { it.copy(email = user.email) }
                viewModelScope.launch { cooldownTicks.collect { publishRemaining() } }
            }
        }
    }

    /** The screen came to the front: check quietly, let them through if verified. */
    fun onScreenShown() {
        check(quiet = true)
    }

    /** "I've verified my email": check, and say so if it is not done yet. */
    fun onCheckClicked() {
        check(quiet = false)
    }

    fun onResendClicked() {
        if (!_uiState.value.resendEnabled) return
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val failure = authRepository.sendEmailVerification().exceptionOrNull()
            _uiState.update { it.copy(isSending = false) }
            when (failure) {
                null -> {
                    startCooldown()
                    _events.send(VerifyRequiredEvent.ShowMessage(MSG_SENT))
                }

                AuthError.TooManyRequests -> {
                    startCooldown()
                    _events.send(VerifyRequiredEvent.ShowMessage(MSG_TOO_MANY_REQUESTS))
                }

                AuthError.NotSignedIn -> _events.send(VerifyRequiredEvent.NavigateToLogin)
                AuthError.Network -> _events.send(VerifyRequiredEvent.ShowMessage(MSG_NO_CONNECTION))
                else -> _events.send(VerifyRequiredEvent.ShowMessage(MSG_SEND_FAILED))
            }
        }
    }

    fun onLogOutClicked() {
        if (_uiState.value.isBusy) return
        authRepository.signOut()
        _events.trySend(VerifyRequiredEvent.LoggedOut)
    }

    fun onDeleteAccountClicked() {
        if (_uiState.value.isBusy) return
        _events.trySend(VerifyRequiredEvent.OpenAccount)
    }

    // ── Checking ──────────────────────────────────────────────────────────────

    private fun check(quiet: Boolean) {
        val state = _uiState.value
        if (state.isChecking) return
        if (authRepository.currentUser() == null) {
            _events.trySend(VerifyRequiredEvent.NavigateToLogin)
            return
        }
        _uiState.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            val failure = authRepository.reloadUser().exceptionOrNull()
            _uiState.update { it.copy(isChecking = false) }
            when {
                failure == null && !isStillRequired() ->
                    _events.send(VerifyRequiredEvent.ContinueToHome)

                // The account was deleted or disabled elsewhere: on either
                // check, or the gate would hold a dead account forever.
                failure == AuthError.NotSignedIn ||
                    failure == AuthError.UserDisabled ||
                    failure == AuthError.NoSuchUser ->
                    _events.send(VerifyRequiredEvent.NavigateToLogin)

                quiet -> Unit

                failure == null ->
                    _events.send(VerifyRequiredEvent.ShowMessage(MSG_NOT_YET))

                failure == AuthError.Network ->
                    _events.send(VerifyRequiredEvent.ShowMessage(MSG_NO_CONNECTION))

                else -> _events.send(VerifyRequiredEvent.ShowMessage(MSG_CHECK_FAILED))
            }
        }
    }

    private fun isStillRequired(): Boolean =
        EmailVerification.of(
            providers = authRepository.currentUserProviders(),
            isEmailVerified = authRepository.isEmailVerified()
        ).requiredBeforeUse

    // ── Cooldown ──────────────────────────────────────────────────────────────

    private fun startCooldown() {
        resendAvailableAt = clock.nowMillis() + RESEND_COOLDOWN_MS
        publishRemaining()
    }

    /** Rounded up, so the button unlocks only once the time has genuinely passed. */
    private fun publishRemaining() {
        val left = resendAvailableAt - clock.nowMillis()
        val seconds = if (left <= 0) 0 else ((left + 999) / 1000).toInt()
        _uiState.update { it.copy(resendSecondsRemaining = seconds) }
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
        const val RESEND_COOLDOWN_MS = RESEND_COOLDOWN_SECONDS * 1000L
        const val TICK_INTERVAL_MS = 1_000L

        const val MSG_NOT_YET =
            "Your email isn't verified yet. Open the link in the email we sent, then try again."
        const val MSG_SENT = "Verification email sent. Open the link in it, then come back here."
        const val MSG_TOO_MANY_REQUESTS =
            "We've sent several emails already. Wait a little, then try again."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_SEND_FAILED = "Could not send the verification email. Please try again."
        const val MSG_CHECK_FAILED = "Could not check your email right now. Please try again."

        private fun defaultCooldownTicks(): Flow<Unit> = flow {
            while (true) {
                delay(TICK_INTERVAL_MS)
                emit(Unit)
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { VerifyRequiredViewModel(RepositoryProvider.authRepository) }
        }
    }
}
