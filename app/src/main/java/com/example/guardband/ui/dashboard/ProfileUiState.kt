package com.example.guardband.ui.dashboard

import com.example.guardband.data.model.User

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Loaded(val user: User) : ProfileUiState
    data object SignedIn : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}
