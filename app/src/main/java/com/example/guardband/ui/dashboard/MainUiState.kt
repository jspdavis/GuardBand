package com.example.guardband.ui.dashboard

sealed interface MainUiState {
    data object Idle : MainUiState
    data object NavigateToLogin : MainUiState
}
