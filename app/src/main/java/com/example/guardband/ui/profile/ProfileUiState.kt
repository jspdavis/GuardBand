package com.example.guardband.ui.profile

/** Render state for [ProfileFragment]. */
data class ProfileUiState(
    /**
     * True until the `users/{uid}` read lands.
     *
     * The other fields are already populated from the session while this is
     * true, so the screen shows the user's name immediately and only the
     * location arrives late.
     */
    val isLoading: Boolean = false,

    /** Welcome-header name (already falls back to "User"). */
    val userName: String = "",
    val email: String = "",
    /** Already falls back to "Location not set". */
    val location: String = ""
)
