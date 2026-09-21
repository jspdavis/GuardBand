package com.example.guardband.ui.changepass

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.changePasswordSuspend
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChangePasswordViewModel(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChangePasswordUiState>(ChangePasswordUiState.Idle)
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onNewPasswordTyped(newPassword: String, confirmPassword: String) {
        val result = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        _uiState.value = ChangePasswordUiState.PasswordCriteria(
            result.passwordRules,
            hasTyped = newPassword.isNotEmpty()
        )
    }

    fun onChangePasswordClicked(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        var hasError = false

        if (currentPassword.isBlank()) {
            _uiState.value = ChangePasswordUiState.CurrentPasswordError("This field is required.")
            hasError = true
        } else {
            _uiState.value = ChangePasswordUiState.CurrentPasswordError(null)
        }
        if (newPassword.isBlank()) {
            _uiState.value = ChangePasswordUiState.NewPasswordError("This field is required.")
            hasError = true
        } else {
            _uiState.value = ChangePasswordUiState.NewPasswordError(null)
        }
        if (confirmPassword.isBlank()) {
            _uiState.value = ChangePasswordUiState.ConfirmPasswordError("This field is required.")
            hasError = true
        } else {
            _uiState.value = ChangePasswordUiState.ConfirmPasswordError(null)
        }
        if (hasError) return

        val passwordValidation = ValidationResult.evaluatePassword(newPassword, confirmPassword)
        when {
            !passwordValidation.passwordRules.minLength -> {
                _uiState.value = ChangePasswordUiState.NewPasswordError("Password must be at least 8 characters.")
                return
            }
            !passwordValidation.passwordRules.hasUppercase -> {
                _uiState.value = ChangePasswordUiState.NewPasswordError("Password must contain at least 1 uppercase letter.")
                return
            }
            !passwordValidation.passwordRules.hasLowercase -> {
                _uiState.value = ChangePasswordUiState.NewPasswordError("Password must contain at least 1 lowercase letter.")
                return
            }
            !passwordValidation.passwordRules.hasNumberOrSpecial -> {
                _uiState.value = ChangePasswordUiState.NewPasswordError("Password must contain at least 1 number or special character.")
                return
            }
            !passwordValidation.passwordRules.passwordsMatch -> {
                _uiState.value = ChangePasswordUiState.ConfirmPasswordError("Passwords do not match.")
                return
            }
        }

        if (currentPassword == newPassword) {
            _uiState.value = ChangePasswordUiState.NewPasswordError("New password must be different from current password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ChangePasswordUiState.Loading
            try {
                authRepository.changePasswordSuspend(currentPassword, newPassword)
                Log.d(TAG, "Password changed successfully")
                _uiState.value = ChangePasswordUiState.Success
                delay(1500L)
                _uiState.value = ChangePasswordUiState.NavigateToDashboard
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg == "CURRENT_PASSWORD_WRONG") {
                    _uiState.value = ChangePasswordUiState.CurrentPasswordError("Current password is incorrect.")
                } else {
                    _uiState.value = ChangePasswordUiState.Error(msg.ifBlank { "Authentication failed. Please try again." })
                }
            }
        }
    }

    fun onCancelClicked() {
        _uiState.value = ChangePasswordUiState.NavigateToDashboard
    }

    companion object {
        private const val TAG = "ChangePasswordViewModel"
    }

    class Factory(private val authRepository: AuthRepository = AuthRepository.getInstance()) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChangePasswordViewModel(authRepository) as T
    }
}
