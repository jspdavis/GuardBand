package com.example.guardband.ui.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.ui.track.Clock
import com.example.guardband.utils.InputValidator
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
 * Forgot Password — request a reset link, then wait for the email.
 *
 * One screen with two faces: the email form, and a "check your email" state
 * with Resend. The confirmation used to be a second Activity; it became state
 * when Resend arrived, because resending needs the address and only this
 * screen has it.
 *
 * **The link in that email opens this app, not Firebase's hosted page** — see
 * [ResetPasswordActivity][com.example.guardband.ui.reset.ResetPasswordActivity]
 * and `docs/forgot-password.md`. Nothing about that changes this screen: it
 * still just asks Firebase to send the mail, and Firebase still decides what
 * the link points at, from the Console's email template.
 *
 * **There is no typed verification code, and there cannot be one** without a
 * server. Generating, storing and checking a six-digit code needs somewhere
 * trusted to keep it and the Admin SDK to set the password afterwards; doing it
 * in the app or in RTDB would put the thing that authorises a password change
 * where the attacker already is. The wireframe's five-box code screen is
 * deliberately not built.
 *
 * **The screen must look identical whether or not the address is registered.**
 * `AuthError.NoSuchUser` and `AuthError.InvalidEmail` are both reported as
 * success, so this screen cannot be used to find out who has an account. Only
 * failures that say nothing about the account — no network, rate limited — are
 * shown as errors, because there the request genuinely did not happen.
 *
 * @param clock read for the resend countdown. Injected so a test never waits.
 * @param cooldownTicks drives the countdown's recompute. Injected rather than
 *   looped internally so a test can step it by hand, or pass an empty flow and
 *   be sure nothing is ticking.
 */
class ForgotRequestViewModel(
    private val authRepository: AuthRepository,
    private val clock: Clock = Clock.SYSTEM,
    private val cooldownTicks: Flow<Unit> = defaultCooldownTicks()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotRequestUiState())
    val uiState: StateFlow<ForgotRequestUiState> = _uiState.asStateFlow()

    private val _events = Channel<ForgotRequestEvent>(Channel.BUFFERED)
    val events: Flow<ForgotRequestEvent> = _events.receiveAsFlow()

    /**
     * When Resend becomes available, as a clock reading rather than a
     * remaining count. Kept outside [uiState] because it is an input to what
     * the screen shows, not something it renders.
     */
    private var resendAvailableAt: Long = 0

    init {
        viewModelScope.launch {
            cooldownTicks.collect { publishRemaining() }
        }
    }

    /**
     * Send, and also Resend: the Activity calls this with the address in the
     * field either way.
     *
     * [email] goes to the repository untrimmed; the repository trims it.
     */
    fun onSendClicked(email: String) {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.sent && state.resendSecondsRemaining > 0) {
            // The button is disabled, so this is a stray tap rather than
            // something to explain.
            return
        }

        val error = when {
            InputValidator.isBlank(email) -> MSG_EMAIL_REQUIRED
            !InputValidator.isValidEmail(email) -> MSG_EMAIL_INVALID
            else -> null
        }
        if (error != null) {
            _events.trySend(ForgotRequestEvent.ShowMessage(error))
            return
        }

        // Captured before the call, because startCooldown sets `sent` itself
        // and the message depends on which tap this was.
        val wasSent = state.sent

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            val failure = result.exceptionOrNull()

            if (failure == null || treatAsSent(failure)) {
                startCooldown()
                if (wasSent) {
                    // A resend, not the first send: say something, or the tap
                    // looks like it did nothing. Sent on the same outcomes as
                    // the first send, enumeration included, so the two cannot
                    // be told apart by whether a confirmation appeared.
                    _events.send(ForgotRequestEvent.ShowMessage(MSG_RESENT))
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(ForgotRequestEvent.ShowMessage(messageFor(failure)))
            }
        }
    }

    /**
     * Whether a failure is reported to the user as success.
     *
     * "No such account" and "malformed email" are, so the screen cannot be
     * used to test which addresses are registered. Both Login and this screen
     * decline to answer that question, and Firebase's own email-enumeration
     * protection returns the same code for both anyway — this is the UI
     * refusing to undo it.
     */
    private fun treatAsSent(error: Throwable): Boolean =
        error == AuthError.NoSuchUser || error == AuthError.InvalidEmail

    private fun startCooldown() {
        resendAvailableAt = clock.nowMillis() + RESEND_COOLDOWN_MS
        _uiState.update { it.copy(isLoading = false, sent = true) }
        publishRemaining()
    }

    /**
     * Recomputes the countdown from the clock.
     *
     * Rounded up, so a cooldown just started reads as the full
     * [RESEND_COOLDOWN_SECONDS] rather than one less, and the button unlocks
     * only once the time has genuinely passed.
     */
    private fun publishRemaining() {
        val left = resendAvailableAt - clock.nowMillis()
        val seconds = if (left <= 0) 0 else ((left + 999) / 1000).toInt()
        _uiState.update { it.copy(resendSecondsRemaining = seconds) }
    }

    /** Wording for a failure that is genuinely shown. Never the exception text. */
    private fun messageFor(error: Throwable): String = when (error) {
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        else -> MSG_REQUEST_FAILED
    }

    companion object {
        /**
         * How long Resend stays locked.
         *
         * Firebase does rate-limit reset emails, but it does not publish the
         * window and answers with a generic `ERROR_TOO_MANY_REQUESTS` when it
         * is hit, so there is no figure to match. A minute is long enough that
         * a user checks their inbox before tapping again, and short enough not
         * to strand someone whose mail genuinely did not arrive.
         */
        const val RESEND_COOLDOWN_SECONDS = 60
        const val RESEND_COOLDOWN_MS = RESEND_COOLDOWN_SECONDS * 1000L

        /** One second, because the countdown is shown to the second. */
        const val TICK_INTERVAL_MS = 1_000L

        const val MSG_EMAIL_REQUIRED = "Please enter your email address."
        const val MSG_EMAIL_INVALID = "Enter a valid email address."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_REQUEST_FAILED = "Could not send the reset email. Please try again."
        const val MSG_RESENT = "Reset link sent again."

        /** The real ticker. A plain loop, so nothing schedules work when unused. */
        private fun defaultCooldownTicks(): Flow<Unit> = flow {
            while (true) {
                delay(TICK_INTERVAL_MS)
                emit(Unit)
            }
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ForgotRequestViewModel(RepositoryProvider.authRepository) }
        }
    }
}
