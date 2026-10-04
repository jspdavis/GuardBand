package com.example.guardband.ui.contacts

import com.example.guardband.data.model.EmergencyContact

/** Render state for [ContactsFragment]. */
data class ContactsUiState(
    /** True while contacts load or a delete is in flight. */
    val isLoading: Boolean = false,
    val contacts: List<EmergencyContact> = emptyList()
)
