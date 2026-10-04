package com.example.guardband.ui.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
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
 * Forgot Password — the only step. Validates the email and asks Firebase to
 * send a reset link; the user finishes on Firebase's own hosted page.
 *
 * The verify-code and set-new-password screens that used to follow are gone:
 * the app never sees a reset code, so it had nothing to check.
 *
 * Flow: ForgotRequestActivity → ForgotSuccessActivity
 */
class ForgotRequestViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotRequestUiState())
    val uiState: StateFlow<ForgotRequestUiState> = _uiState.asStateFlow()

    private val _events = Channel<ForgotRequestEvent>(Channel.BUFFERED)
    val events: Flow<ForgotRequestEvent> = _events.receiveAsFlow()

    /** [email] goes to the repository untrimmed; the repository trims it. */
    fun onSendClicked(email: String) {
        if (_uiState.value.isLoading) return
        val error = when {
            InputValidator.isBlank(email) -> MSG_EMAIL_REQUIRED
            !InputValidator.isValidEmail(email) -> MSG_EMAIL_INVALID
            else -> null
        }
        if (error != null) {
            _events.trySend(ForgotRequestEvent.ShowMessage(error))
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotRequestEvent.NavigateToSent)
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(eventFor(failure))
                }
        }
    }

    /**
     * Decides what a failure looks like to the user.
     *
     * "No such account" and "malformed email" are reported as success, so the
     * screen cannot be used to find out which emails are registered. Only
     * failures that say nothing about the account — no network, rate limited —
     * are shown as errors, because there the request genuinely did not happen
     * and the user needs to retry.
     */
    private fun eventFor(error: Throwable): ForgotRequestEvent = when (error) {
        AuthError.NoSuchUser,
        AuthError.InvalidEmail -> ForgotRequestEvent.NavigateToSent

        AuthError.Network -> ForgotRequestEvent.ShowMessage(MSG_NO_CONNECTION)
        AuthError.TooManyRequests -> ForgotRequestEvent.ShowMessage(MSG_TOO_MANY_ATTEMPTS)
        else -> ForgotRequestEvent.ShowMessage(MSG_REQUEST_FAILED)
    }

    companion object {
        const val MSG_EMAIL_REQUIRED = "Please enter your email address."
        const val MSG_EMAIL_INVALID = "Enter a valid email address."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_REQUEST_FAILED = "Could not send the reset email. Please try again."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ForgotRequestViewModel(RepositoryProvider.authRepository) }
        }
    }
}
