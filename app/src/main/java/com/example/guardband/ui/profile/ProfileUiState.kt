package com.example.guardband.ui.profile

/** Render state for [ProfileFragment]. */
data class ProfileUiState(
    /** Welcome-header name (already falls back to "User"). */
    val userName: String = "",
    val email: String = "",
    /** Already falls back to "Location not set". */
    val location: String = ""
)
