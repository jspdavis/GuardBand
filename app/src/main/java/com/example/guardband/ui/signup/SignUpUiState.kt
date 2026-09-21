package com.example.guardband.ui.signup

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ValidationResult

sealed interface SignUpUiState {
    data object Idle : SignUpUiState
    data object Loading : SignUpUiState
    data class NavigateToStep(val step: Int) : SignUpUiState
    data class FieldError(val field: String, val message: String?) : SignUpUiState
    data class PasswordCriteria(
        val rules: ValidationResult.PasswordRules,
        val hasTyped: Boolean
    ) : SignUpUiState
    data class ContactsUpdated(val contacts: List<EmergencyContact>) : SignUpUiState
    data object NavigateToLoading : SignUpUiState
    data class Error(val message: String) : SignUpUiState
}
