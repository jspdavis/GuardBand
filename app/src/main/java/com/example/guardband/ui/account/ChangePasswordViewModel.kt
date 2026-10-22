package com.example.guardband.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.SignInProvider
import com.example.guardband.data.repository.AuthError
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.utils.InputValidator
import com.example.guardband.utils.PasswordCriteria
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What [ChangePasswordActivity] draws. */
data class ChangePasswordUiState(
    val criteria: PasswordCriteria = PasswordCriteria.NONE,
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

/** One-shot events emitted by [ChangePasswordViewModel]. */
sealed interface ChangePasswordEvent {
    data class ShowMessage(val text: String) : ChangePasswordEvent

    /** Ask for the current password, in a dialog. */
    object AskForPassword : ChangePasswordEvent

    /** Leave the screen, back to Settings. */
    object Close : ChangePasswordEvent

    /** The session is gone: Login, back stack cleared. */
    object NavigateToLogin : ChangePasswordEvent
}

/**
 * Change password, opened from Settings. **Password accounts only**: a Google
 * account has no password to change, so Settings refuses it and this ViewModel
 * checks again, in case the screen is ever reached another way.
 *
 * Order: check the typed pair, ask for the current password in a dialog (the
 * same re-sign-in step Delete account uses), re-authenticate, then update.
 * Firebase requires the recent login, and a password change should ask anyway.
 *
 * The new password is passed in by the screen on each call and held nowhere
 * here. Nothing is logged, and no Firebase text reaches a message.
 */
class ChangePasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events: Flow<ChangePasswordEvent> = _events.receiveAsFlow()

    init {
        if (SignInProvider.PASSWORD !in authRepository.currentUserProviders()) {
            _events.trySend(ChangePasswordEvent.ShowMessage(MSG_NO_PASSWORD))
            _events.trySend(ChangePasswordEvent.Close)
        }
    }

    /** Every keystroke in either field. */
    fun onFieldsChanged(newPassword: String, confirm: String) {
        _uiState.update {
            it.copy(criteria = PasswordCriteria.of(newPassword, confirm), errorMessage = null)
        }
    }

    /** Submit: validates the pair, then asks who they are. */
    fun onSubmitClicked(newPassword: String, confirm: String) {
        if (_uiState.value.isBusy) return
        if (!PasswordCriteria.of(newPassword, confirm).allMet) {
            _uiState.update { it.copy(errorMessage = MSG_RULES_NOT_MET) }
            return
        }
        if (authRepository.currentUser() == null) {
            sessionGone()
            return
        }
        _events.trySend(ChangePasswordEvent.AskForPassword)
    }

    /**
     * The current password from the dialog.
     *
     * Only blankness is checked: this proves a password, it does not choose
     * one, and an older account may hold one shorter than today's rule.
     */
    fun onPasswordSubmitted(current: String, newPassword: String, confirm: String) {
        if (_uiState.value.isBusy) return
        if (InputValidator.isBlank(current)) {
            _events.trySend(ChangePasswordEvent.ShowMessage(MSG_PASSWORD_REQUIRED))
            _events.trySend(ChangePasswordEvent.AskForPassword)
            return
        }
        // The fields could have changed while the dialog was up.
        if (!PasswordCriteria.of(newPassword, confirm).allMet) {
            _uiState.update { it.copy(errorMessage = MSG_RULES_NOT_MET) }
            return
        }

        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val reauthError = authRepository.reauthenticateWithPassword(current).exceptionOrNull()
            if (reauthError != null) {
                if (reauthError == AuthError.NotSignedIn) sessionGone() else fail(reauthMessage(reauthError))
                return@launch
            }

            val updateError = authRepository.updatePassword(newPassword).exceptionOrNull()
            if (updateError != null) {
                if (updateError == AuthError.NotSignedIn) sessionGone() else fail(updateMessage(updateError))
                return@launch
            }

            _uiState.update { it.copy(isBusy = false) }
            _events.send(ChangePasswordEvent.ShowMessage(MSG_CHANGED))
            _events.send(ChangePasswordEvent.Close)
        }
    }

    /** Back or Cancel. */
    fun onCancelled() {
        if (_uiState.value.isBusy) return
        _events.trySend(ChangePasswordEvent.Close)
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(isBusy = false, errorMessage = message) }
    }

    private fun sessionGone() {
        _uiState.update { it.copy(isBusy = false) }
        _events.trySend(ChangePasswordEvent.ShowMessage(MSG_SESSION_EXPIRED))
        _events.trySend(ChangePasswordEvent.NavigateToLogin)
    }

    /** A failure proving who they are. Never shows the exception text. */
    private fun reauthMessage(error: Throwable): String = when (error) {
        AuthError.InvalidCredentials -> MSG_WRONG_PASSWORD
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        AuthError.UserDisabled -> MSG_ACCOUNT_UNAVAILABLE
        else -> MSG_REAUTH_FAILED
    }

    /** A failure setting the new password. */
    private fun updateMessage(error: Throwable): String = when (error) {
        AuthError.WeakPassword -> MSG_WEAK_PASSWORD
        AuthError.RequiresRecentLogin -> MSG_NEEDS_RECENT_LOGIN
        AuthError.Network -> MSG_NO_CONNECTION
        AuthError.TooManyRequests -> MSG_TOO_MANY_ATTEMPTS
        else -> MSG_UPDATE_FAILED
    }

    companion object {
        const val MSG_NO_PASSWORD = "You sign in with Google, so there is no password to change."
        const val MSG_RULES_NOT_MET = "Your new password doesn't meet the rules above."
        const val MSG_PASSWORD_REQUIRED = "Enter your current password to confirm."
        const val MSG_WRONG_PASSWORD = "Your current password is incorrect."
        const val MSG_WEAK_PASSWORD = "That password is too weak. Choose a longer one."
        const val MSG_NEEDS_RECENT_LOGIN = "Please try again and confirm it's you."
        const val MSG_NO_CONNECTION = "No connection. Check your network and try again."
        const val MSG_TOO_MANY_ATTEMPTS = "Too many attempts. Try again in a few minutes."
        const val MSG_ACCOUNT_UNAVAILABLE = "This account is no longer available."
        const val MSG_REAUTH_FAILED = "Could not confirm it's you. Please try again."
        const val MSG_UPDATE_FAILED = "Could not change your password. Please try again."
        const val MSG_SESSION_EXPIRED = "You are no longer signed in. Please sign in again."
        const val MSG_CHANGED = "Password changed."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ChangePasswordViewModel(RepositoryProvider.authRepository) }
        }
    }
}