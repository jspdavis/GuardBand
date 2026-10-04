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
 * Forgot Password — Step 2. Verifies the code (mock code: "123456") and
 * handles "resend code".
 *
 * Flow: ForgotVerifyActivity → ForgotNewPassActivity
 */
class ForgotVerifyViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotVerifyUiState())
    val uiState: StateFlow<ForgotVerifyUiState> = _uiState.asStateFlow()

    private val _events = Channel<ForgotVerifyEvent>(Channel.BUFFERED)
    val events: Flow<ForgotVerifyEvent> = _events.receiveAsFlow()

    /** [email] is the value received from step 1, forwarded as-is. */
    fun onVerifyClicked(email: String, code: String) {
        if (_uiState.value.isLoading) return
        if (InputValidator.isBlank(code)) {
            _events.trySend(ForgotVerifyEvent.ShowMessage(MSG_CODE_REQUIRED))
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.verifyResetCode(email, code)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotVerifyEvent.NavigateToNewPassword(email))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotVerifyEvent.ShowMessage(error.message.orEmpty()))
                }
        }
    }

    fun onResendClicked(email: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.requestPasswordReset(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotVerifyEvent.ShowMessage(MSG_CODE_RESENT))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(ForgotVerifyEvent.ShowMessage(error.message.orEmpty()))
                }
        }
    }

    companion object {
        const val MSG_CODE_REQUIRED = "Please enter the verification code."
        const val MSG_CODE_RESENT = "Code resent — check your email."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ForgotVerifyViewModel(RepositoryProvider.authRepository) }
        }
    }
}
