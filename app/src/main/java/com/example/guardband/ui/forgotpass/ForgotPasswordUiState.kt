package com.example.guardband.ui.forgotpass

import com.example.guardband.data.model.ValidationResult

sealed interface ForgotPasswordUiState {
    data object Idle : ForgotPasswordUiState
    data object Loading : ForgotPasswordUiState
    data class ShowStep(val step: Int, val email: String = "") : ForgotPasswordUiState
    data class TimerTick(val text: String) : ForgotPasswordUiState
    data object TimerFinished : ForgotPasswordUiState
    data class PasswordRulesUpdate(
        val rules: ValidationResult.PasswordRules,
        val hasTyped: Boolean
    ) : ForgotPasswordUiState
    data class RequiredFieldErrors(val show: Boolean) : ForgotPasswordUiState
    data class PasswordMismatchError(val show: Boolean) : ForgotPasswordUiState
    data class CredentialBorders(val valid: Boolean?, val error: Boolean) : ForgotPasswordUiState
    data class DebugOtp(val otp: String) : ForgotPasswordUiState
    data object NavigateToLoginCleared : ForgotPasswordUiState
    data class Error(val message: String) : ForgotPasswordUiState
}
