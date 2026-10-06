package com.example.guardband.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.ui.auth.GoogleIdTokenResult
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
 * Login screen logic: credential validation, the login and Google sign-in
 * calls, and navigation.
 *
 * Holds no Credential Manager type and no Context. The Activity opens the
 * account chooser and hands back either the ID token string
 * ([onGoogleIdToken]) or a [GoogleIdTokenResult] describing what went wrong
 * ([onGoogleError]).
 *
 * Flow:
 *   Login  →  LoadingActivity  →  HomeActivity
 *   Login  →  SignUpAccountActivity
 *   Login  →  ForgotRequestActivity
 *   Login  →  (Google, returning user)  →  LoadingActivity  →  HomeActivity
 *   Login  →  (Google, first-time user) →  SignUpContactsActivity (complete profile)
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
        if (_uiState.value.isBusy) return
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

    // ── Google sign-in ────────────────────────────────────────────────────────

    /**
     * Google button tapped: asks the Activity for a token and shows progress
     * from here, because the chooser can sit on screen for a while.
     */
    fun onGoogleSignInClicked() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isGoogleLoading = true) }
        _events.trySend(LoginEvent.RequestGoogleIdToken)
    }

    /**
     * Exchanges [idToken] for a Firebase session and routes on the result: a
     * returning user goes to Home, a first-time user into complete-profile.
     *
     * The token is never logged, and never kept after the call.
     */
    fun onGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            authRepository.signInWithGoogle(idToken)
                .onSuccess { outcome ->
                    _uiState.update { it.copy(isGoogleLoading = false) }
                    _events.send(
                        if (outcome.isNewUser) {
                            LoginEvent.NavigateToCompleteProfile(
                                name = outcome.user.name,
                                email = outcome.user.email
                            )
                        } else {
                            LoginEvent.NavigateToLoadingHome
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isGoogleLoading = false) }
                    _events.send(LoginEvent.ShowMessage(googleMessageFor(error)))
                }
        }
    }

    /**
     * The chooser did not produce a token.
     *
     * [GoogleIdTokenResult.Cancelled] is silent: the user dismissed the sheet
     * deliberately and does not need to be told what they just did.
     */
    fun onGoogleError(result: GoogleIdTokenResult) {
        _uiState.update { it.copy(isGoogleLoading = false) }
        val message = when (result) {
            GoogleIdTokenResult.Cancelled -> return
            GoogleIdTokenResult.NoGoogleAccount -> MSG_NO_GOOGLE_ACCOUNT
            GoogleIdTokenResult.Unavailable -> MSG_GOOGLE_UNAVAILABLE
            GoogleIdTokenResult.Network -> MSG_NO_CONNECTION
            else -> MSG_GOOGLE_FAILED
        }
        _events.trySend(LoginEvent.ShowMessage(message))
    }

    // ── Navigation ────────────────────────────────────────────────────────────

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

    /**
     * Wording for an [AuthError] from the Google exchange.
     *
     * The collision case gets a neutral message that does not name the other
     * provider: saying "this address uses a password" would confirm that the
     * account exists, which is what the shared invalid-credentials message
     * exists to avoid.
     */
    private fun googleMessageFor(error: Throwable): String = when (error) {
        AuthError.AccountExistsWithDifferentCredential -> MSG_DIFFERENT_SIGN_IN_METHOD
        AuthError.UserDisabled -> MSG_ACCOUNT_DISABLED
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        else -> MSG_GOOGLE_FAILED
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
        const val MSG_DIFFERENT_SIGN_IN_METHOD = "This email uses a different sign-in method."
        const val MSG_NO_GOOGLE_ACCOUNT = "No Google account on this device. Add one and try again."
        const val MSG_GOOGLE_UNAVAILABLE = "Google sign-in is unavailable on this device."
        const val MSG_GOOGLE_FAILED = "Could not sign in with Google. Please try again."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(RepositoryProvider.authRepository) }
        }
    }
}
