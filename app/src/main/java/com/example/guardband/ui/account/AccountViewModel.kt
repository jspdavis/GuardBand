package com.example.guardband.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
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
 * Account information: who is signed in, and whether their email is verified.
 * Delete account, at the bottom of the same screen, is [DeleteAccountViewModel]'s.
 *
 * Everything comes from the session, synchronously, so the screen is never
 * blank. The email shown is the one the account signs in with, which is the
 * one that matters here; Profile is where the stored `users/{uid}` record is
 * read.
 *
 * **Verification is refreshed every time the screen is shown**
 * ([onScreenShown]): the user taps the link in their inbox, Firebase marks
 * the address verified, and the session only learns of it on a reload. The
 * reload never signs anyone out, and a failed one leaves the status as it was.
 * [EmailVerification] decides what is said; nothing in the app is gated on it.
 *
 * **Resend has a cooldown**, [RESEND_COOLDOWN_SECONDS], recomputed from an
 * injected [Clock] on each tick of an injected [Flow] - the same pattern as
 * the forgot-password screen's Resend, so a test never waits on real time.
 * Firebase rate-limits verification emails on its own and answers
 * [AuthError.TooManyRequests]; that is worded kindly and starts the cooldown
 * too. The cooldown lives in this ViewModel, so leaving the screen resets it;
 * Firebase's own limit is the backstop.
 *
 * **Its own auth gate.** [AccountActivity] is outside the Home host, so
 * [HomeViewModel][com.example.guardband.ui.home.HomeViewModel]'s check does
 * not cover it; a restore with no session goes to Login instead of showing an
 * empty account.
 *
 * Nothing here logs: not the name, not the email.
 */
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val clock: Clock = Clock.SYSTEM,
    private val cooldownTicks: Flow<Unit> = defaultCooldownTicks()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events: Flow<AccountEvent> = _events.receiveAsFlow()

    /** When Resend unlocks, as a clock reading. */
    private var resendAvailableAt: Long = 0

    init {
        if (authRepository.currentUser() == null) {
            _events.trySend(AccountEvent.NavigateToLogin)
        } else {
            publishSession()
            viewModelScope.launch {
                cooldownTicks.collect { publishRemaining() }
            }
        }
    }

    /**
     * The screen came to the front - first open, or back from the email app.
     * Reloads the session so a link opened meanwhile shows as "Verified".
     */
    fun onScreenShown() {
        if (authRepository.currentUser() == null) return
        viewModelScope.launch {
            if (authRepository.reloadUser().isSuccess) publishSession()
        }
    }

    fun onResendClicked() {
        val state = _uiState.value
        if (!state.resendEnabled) return

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val failure = authRepository.sendEmailVerification().exceptionOrNull()
            _uiState.update { it.copy(isSending = false) }
            when (failure) {
                null -> {
                    startCooldown()
                    _events.send(AccountEvent.ShowMessage(MSG_SENT))
                }

                AuthError.TooManyRequests -> {
                    // Firebase's own limit: waiting is the only fix, so the
                    // button waits too rather than inviting another refusal.
                    startCooldown()
                    _events.send(AccountEvent.ShowMessage(MSG_TOO_MANY_REQUESTS))
                }

                AuthError.NotSignedIn -> _events.send(AccountEvent.NavigateToLogin)
                AuthError.Network -> _events.send(AccountEvent.ShowMessage(MSG_NO_CONNECTION))
                else -> _events.send(AccountEvent.ShowMessage(MSG_SEND_FAILED))
            }
        }
    }

    /** Name, email and verification, from the session as it is now. */
    private fun publishSession() {
        val user = authRepository.currentUser() ?: return
        val verification = EmailVerification.of(
            providers = authRepository.currentUserProviders(),
            isEmailVerified = authRepository.isEmailVerified()
        )
        _uiState.update {
            it.copy(
                name = user.name,
                email = user.email,
                emailVerified = verification.verified,
                canResend = verification.canResend
            )
        }
    }

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
        /** The same minute as forgot-password's Resend, for the same reasons. */
        const val RESEND_COOLDOWN_SECONDS = 60
        const val RESEND_COOLDOWN_MS = RESEND_COOLDOWN_SECONDS * 1000L
        const val TICK_INTERVAL_MS = 1_000L

        const val MSG_SENT = "Verification email sent. Open the link in it, then come back here."
        const val MSG_TOO_MANY_REQUESTS =
            "We've sent several emails already. Wait a little, then try again."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_SEND_FAILED = "Could not send the verification email. Please try again."

        private fun defaultCooldownTicks(): Flow<Unit> = flow {
            while (true) {
                delay(TICK_INTERVAL_MS)
                emit(Unit)
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(RepositoryProvider.authRepository) }
        }
    }
}
