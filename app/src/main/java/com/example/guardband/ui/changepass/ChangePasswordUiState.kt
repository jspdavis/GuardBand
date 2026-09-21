package com.example.guardband.ui.changepass

import com.example.guardband.data.model.ValidationResult

sealed interface ChangePasswordUiState {
    data object Idle : ChangePasswordUiState
    data object Loading : ChangePasswordUiState
    data object Success : ChangePasswordUiState
    data class CurrentPasswordError(val message: String?) : ChangePasswordUiState
    data class NewPasswordError(val message: String?) : ChangePasswordUiState
    data class ConfirmPasswordError(val message: String?) : ChangePasswordUiState
    data class PasswordCriteria(
        val rules: ValidationResult.PasswordRules,
        val hasTyped: Boolean
    ) : ChangePasswordUiState
    data object NavigateToDashboard : ChangePasswordUiState
    data class Error(val message: String) : ChangePasswordUiState
}
