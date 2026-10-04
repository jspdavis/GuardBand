package com.example.guardband.ui.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
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
 * Forgot Password — Step 3. Validates and saves the new password.
 *
 * Flow: ForgotNewPassActivity → ForgotSuccessActivity
 */
class ForgotNewPassViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotNewPassUiState())
    val uiState: StateFlow<ForgotNewPassUiState> = _uiState.asStateFlow()

    private val _events = Channel<ForgotNewPassEvent>(Channel.BUFFERED)
    val events: Flow<ForgotNewPassEvent> = _events.receiveAsFlow()

    /** [email] is the value received from step 2; passwords are not trimmed. */
    fun onSaveClicked(email: String, newPassword: String, confirmPassword: String) {
        if (_uiState.value.isLoading) return
        val error = when {
            InputValidator.isBlank(newPassword) -> MSG_PASSWORD_REQUIRED
            !InputValidator.isPasswordLongEnough(newPassword) -> MSG_PASSWORD_TOO_SHORT
            !InputValidator.passwordsMatch(newPassword, confirmPassword) -> MSG_PASSWORDS_MISMATCH
            else -> null
        }
        if (error != null) {
            _events.trySend(ForgotNewPassEvent.ShowMessage(error))
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.resetPassword(email, newPassword)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotNewPassEvent.NavigateToSuccess)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotNewPassEvent.ShowMessage(error.message.orEmpty()))
                }
        }
    }

    companion object {
        const val MSG_PASSWORD_REQUIRED = "Please enter a new password."
        const val MSG_PASSWORD_TOO_SHORT = "Password must be at least 6 characters."
        const val MSG_PASSWORDS_MISMATCH = "Passwords do not match."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ForgotNewPassViewModel(RepositoryProvider.authRepository) }
        }
    }
}
