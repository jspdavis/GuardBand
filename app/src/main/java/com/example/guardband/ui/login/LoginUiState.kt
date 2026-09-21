package com.example.guardband.ui.login

import com.example.guardband.data.model.User

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(val message: String) : LoginUiState
    /** Inline validation error on the identifier field only. */
    data class IdentifierError(val message: String?) : LoginUiState
}
