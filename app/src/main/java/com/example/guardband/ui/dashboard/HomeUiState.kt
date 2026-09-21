package com.example.guardband.ui.dashboard

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class WelcomeMessage(val fullName: String) : HomeUiState
    data object DefaultMessage : HomeUiState
    data class Error(val message: String) : HomeUiState
}
