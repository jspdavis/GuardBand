package com.example.guardband.ui.login

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
 * Login screen logic: credential validation, the login call, and navigation.
 *
 * Flow:
 *   Login  →  LoadingActivity  →  HomeActivity
 *   Login  →  SignUpNameActivity
 *   Login  →  ForgotRequestActivity
 */
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    /** [email] and [password] are passed to the repository untrimmed, as before. */
    fun onLoginClicked(email: String, password: String) {
        if (_uiState.value.isLoading) return
        if (!validateCredentials(email, password)) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.login(email, password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(LoginEvent.NavigateToLoadingHome)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(LoginEvent.ShowMessage(messageFor(error)))
                }
        }
    }

    fun onSignUpClicked() {
        _events.trySend(LoginEvent.NavigateToSignUp)
    }

    fun onForgotPasswordClicked() {
        _events.trySend(LoginEvent.NavigateToForgotPassword)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /** Emits the first failing rule's message and returns false. */
    private fun validateCredentials(email: String, password: String): Boolean {
        val error = when {
            InputValidator.isBlank(email) -> MSG_EMAIL_REQUIRED
            !InputValidator.isValidEmail(email) -> MSG_EMAIL_INVALID
            InputValidator.isBlank(password) -> MSG_PASSWORD_REQUIRED
            else -> return true
        }
        _events.trySend(LoginEvent.ShowMessage(error))
        return false
    }

    // ── Errors ────────────────────────────────────────────────────────────────

    /**
     * Wording for an [AuthError]. Never shows the exception text.
     *
     * A wrong password and an unknown email deliberately share one message:
     * Firebase reports both with the same code, and keeping them identical
     * stops the screen from confirming which emails have accounts.
     */
    private fun messageFor(error: Throwable): String = when (error) {
        AuthError.InvalidCredentials,
        AuthError.NoSuchUser,
        AuthError.InvalidEmail -> MSG_INVALID_CREDENTIALS

        AuthError.UserDisabled -> MSG_ACCOUNT_DISABLED
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        else -> MSG_LOGIN_FAILED
    }

    companion object {
        const val MSG_EMAIL_REQUIRED = "Email is required."
        const val MSG_EMAIL_INVALID = "Enter a valid email address."
        const val MSG_PASSWORD_REQUIRED = "Password is required."
        const val MSG_INVALID_CREDENTIALS = "Email or password is incorrect."
        const val MSG_ACCOUNT_DISABLED = "This account has been disabled."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_LOGIN_FAILED = "Could not log in. Please try again."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(RepositoryProvider.authRepository) }
        }
    }
}
