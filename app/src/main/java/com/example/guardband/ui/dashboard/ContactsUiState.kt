package com.example.guardband.ui.dashboard

import com.example.guardband.data.model.EmergencyContact

sealed interface ContactsUiState {
    data object Loading : ContactsUiState
    data class Contacts(val contacts: List<EmergencyContact>) : ContactsUiState
    data object Empty : ContactsUiState
    data class Error(val message: String) : ContactsUiState
}
