package com.example.guardband.ui.reset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.utils.PasswordCriteria
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * New Credentials — the in-app end of a password reset.
 *
 * The user got here by opening the link in Firebase's reset email, which is an
 * Android App Link onto this app rather than Firebase's hosted page. Firebase
 * has already proved they control that inbox; the `oobCode` in the link is the
 * proof, and it is the only authorisation either call here needs. **Nobody is
 * signed in**, and if someone happens to be, this flow neither uses nor
 * disturbs their session.
 *
 * **The code is held here and nowhere else.** Not in an Intent extra, not in
 * saved instance state, not in a log line, and not in [ResetPasswordUiState] —
 * it is a single-use credential for changing a password, so the only copy is
 * this private field for as long as the ViewModel lives.
 *
 * **What that costs:** the code does not survive process death. If Android
 * kills the app while the screen is open, the user comes back to a screen with
 * no code and has to open the link in the email again. That is the deliberate
 * trade — persisting it anywhere would mean writing somebody's
 * password-change credential to disk.
 *
 * The typed password is never held here at all: the Activity passes it in on
 * each call, which is also why a failed submit "keeps what was typed" for
 * free — the fields are never cleared, so a retry re-sends the same text.
 *
 * Flow: (email link) → ResetPasswordActivity → ResetSuccessActivity → LoginActivity
 * (back stack cleared at each step)
 */
class ResetPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<ResetPasswordEvent>(Channel.BUFFERED)
    val events: Flow<ResetPasswordEvent> = _events.receiveAsFlow()

    /** The single-use code from the link. See the class KDoc for why it lives only here. */
    private var oobCode: String? = null

    // ── Arriving ──────────────────────────────────────────────────────────────

    /**
     * Hands over the code parsed out of the link, and checks it.
     *
     * [code] is null when the URL was not a usable reset link at all, which is
     * reported the same way an expired one is: there is nothing to tell apart
     * from the user's point of view, and saying which check failed would
     * describe the contents of the link.
     *
     * Calling it twice with the same code is harmless — a second call while
     * the first is in flight is ignored, and [ResetPasswordActivity] guards
     * against re-verifying on a configuration change anyway.
     */
    fun onLinkOpened(code: String?) {
        if (_uiState.value.isBusy && oobCode != null) return
        if (code.isNullOrBlank()) {
            linkDead(MSG_LINK_UNUSABLE)
            return
        }
        oobCode = code
        verify(code)
    }

    /** The Retry offered when the code check failed for a network reason. */
    fun onRetryVerifyClicked() {
        val code = oobCode
        if (_uiState.value.isBusy || code == null) return
        verify(code)
    }

    private fun verify(code: String) {
        _uiState.update {
            it.copy(
                stage = ResetPasswordStage.VERIFYING,
                message = null,
                canRetryVerify = false
            )
        }
        viewModelScope.launch {
            authRepository.verifyPasswordResetCode(code)
                .onSuccess { email ->
                    _uiState.update {
                        it.copy(
                            stage = ResetPasswordStage.READY,
                            email = email,
                            message = null,
                            canRetryVerify = false
                        )
                    }
                }
                .onFailure { error ->
                    // A network failure says nothing about the code, so the
                    // same link is still worth trying. Anything else has
                    // settled the question.
                    if (error == AuthError.Network) {
                        _uiState.update {
                            it.copy(
                                stage = ResetPasswordStage.LINK_DEAD,
                                message = MSG_NO_CONNECTION,
                                canRetryVerify = true
                            )
                        }
                    } else {
                        linkDead(verifyMessageFor(error))
                    }
                }
        }
    }

    // ── Typing ────────────────────────────────────────────────────────────────

    /**
     * Re-evaluates the criteria list on every keystroke.
     *
     * The rules come from [PasswordCriteria], which derives them from
     * `InputValidator`, so the ticks the user reads and the gate on Submit
     * cannot disagree. Clears a stale "too weak" from the field, because the
     * password it referred to is no longer the one typed.
     */
    fun onPasswordChanged(password: String, confirmPassword: String) {
        _uiState.update {
            it.copy(
                criteria = PasswordCriteria.of(password, confirmPassword),
                fieldError = null
            )
        }
    }

    // ── Submitting ────────────────────────────────────────────────────────────

    /**
     * Sets the new password.
     *
     * The criteria are re-checked here rather than trusted from the disabled
     * button, which is the same belt-and-braces the consent checkbox gets.
     * Firebase's own minimum is six characters and the app asks for eight, so
     * a server-side [AuthError.WeakPassword] means Firebase got stricter than
     * us and the message goes on the field.
     */
    fun onSubmitClicked(password: String, confirmPassword: String) {
        val state = _uiState.value
        if (state.isBusy || state.stage != ResetPasswordStage.READY) return

        val code = oobCode
        if (code == null) {
            linkDead(MSG_LINK_UNUSABLE)
            return
        }

        val criteria = PasswordCriteria.of(password, confirmPassword)
        if (!criteria.allMet) {
            _uiState.update {
                it.copy(
                    criteria = criteria,
                    fieldError = if (!criteria.longEnough) MSG_PASSWORD_TOO_SHORT
                    else MSG_PASSWORDS_DIFFER
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                stage = ResetPasswordStage.SUBMITTING,
                criteria = criteria,
                fieldError = null,
                message = null
            )
        }
        viewModelScope.launch {
            authRepository.confirmPasswordReset(code, password)
                .onSuccess {
                    _uiState.update {
                        it.copy(stage = ResetPasswordStage.DONE, message = null)
                    }
                    _events.send(ResetPasswordEvent.NavigateToSuccess)
                }
                .onFailure { error -> failSubmit(error) }
        }
    }

    /**
     * Where a failed submit leaves the screen.
     *
     * Most failures go back to [ResetPasswordStage.READY], so the user can fix
     * what they typed and submit the same fields again - the fields are never
     * cleared, so a retry re-sends the same text. An action-code failure does
     * not: the code is single-use and may well have been spent on the attempt,
     * so offering a retry would send them round a loop that cannot succeed.
     *
     * A rejected password is state, because it belongs to the field and has to
     * survive a rotation. A dropped connection is an event, because it belongs
     * to the attempt rather than the input - the same split the rest of the app
     * uses.
     */
    private fun failSubmit(error: Throwable) {
        if (error == AuthError.WeakPassword) {
            _uiState.update {
                it.copy(
                    stage = ResetPasswordStage.READY,
                    fieldError = MSG_PASSWORD_REJECTED,
                    message = null
                )
            }
            return
        }

        if (error == AuthError.ExpiredActionCode || error == AuthError.InvalidActionCode) {
            linkDead(submitMessageFor(error))
            return
        }

        _uiState.update {
            it.copy(
                stage = ResetPasswordStage.READY,
                fieldError = null,
                message = null
            )
        }
        _events.trySend(ResetPasswordEvent.ShowMessage(submitFailureMessageFor(error)))
    }

    /** The user is done with this link; only a new email moves them on. */
    fun onRequestNewLinkClicked() {
        _events.trySend(ResetPasswordEvent.NavigateToRequest)
    }

    private fun linkDead(message: String) {
        _uiState.update {
            it.copy(
                stage = ResetPasswordStage.LINK_DEAD,
                message = message,
                canRetryVerify = false,
                fieldError = null
            )
        }
    }

    // ── Wording ───────────────────────────────────────────────────────────────

    private fun verifyMessageFor(error: Throwable): String = when (error) {
        AuthError.ExpiredActionCode -> MSG_LINK_EXPIRED
        AuthError.InvalidActionCode -> MSG_LINK_USED
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        AuthError.UserDisabled -> MSG_ACCOUNT_UNAVAILABLE
        else -> MSG_LINK_UNUSABLE
    }

    private fun submitMessageFor(error: Throwable): String = when (error) {
        AuthError.ExpiredActionCode -> MSG_LINK_EXPIRED
        else -> MSG_LINK_USED
    }

    /** Wording for a submit failure the user can simply try again. */
    private fun submitFailureMessageFor(error: Throwable): String = when (error) {
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        else -> MSG_RESET_FAILED
    }

    companion object {
        const val MSG_LINK_EXPIRED =
            "This reset link has expired. Request a new one to continue."
        const val MSG_LINK_USED =
            "This reset link has already been used. Request a new one to continue."
        const val MSG_LINK_UNUSABLE =
            "This reset link isn't valid. Request a new one to continue."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_ACCOUNT_UNAVAILABLE = "This account is no longer available."
        const val MSG_RESET_FAILED = "Could not set your new password. Please try again."

        const val MSG_PASSWORD_TOO_SHORT = "Password must be at least 8 characters."
        const val MSG_PASSWORDS_DIFFER = "Passwords do not match."

        /** Firebase refused it, which means its rule is stricter than the app's eight. */
        const val MSG_PASSWORD_REJECTED = "Choose a stronger password."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ResetPasswordViewModel(RepositoryProvider.authRepository) }
        }
    }
}
