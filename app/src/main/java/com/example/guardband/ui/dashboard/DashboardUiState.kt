package com.example.guardband.ui.dashboard

import com.example.guardband.data.model.EmergencyContact

/** Render state for [DashboardActivity]. */
data class DashboardUiState(
    /** True until the contacts have loaded; the list is not rendered meanwhile. */
    val isLoading: Boolean = false,
    /** Display name for the welcome header (already falls back to "User"). */
    val userName: String = "",
    val contacts: List<EmergencyContact> = emptyList()
)
