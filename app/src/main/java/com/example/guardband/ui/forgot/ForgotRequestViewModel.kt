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
 * Forgot Password — Step 1. Validates the email and requests a (mock) reset code.
 *
 * Flow: ForgotRequestActivity → ForgotVerifyActivity
 */
class ForgotRequestViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotRequestUiState())
    val uiState: StateFlow<ForgotRequestUiState> = _uiState.asStateFlow()

    private val _events = Channel<ForgotRequestEvent>(Channel.BUFFERED)
    val events: Flow<ForgotRequestEvent> = _events.receiveAsFlow()

    /** [email] goes to the repository untrimmed; the next step receives it trimmed, as before. */
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
            authRepository.requestPasswordReset(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotRequestEvent.NavigateToVerify(email.trim()))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotRequestEvent.ShowMessage(error.message.orEmpty()))
                }
        }
    }

    companion object {
        const val MSG_EMAIL_REQUIRED = "Please enter your email address."
        const val MSG_EMAIL_INVALID = "Enter a valid email address."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ForgotRequestViewModel(RepositoryProvider.authRepository) }
        }
    }
}
