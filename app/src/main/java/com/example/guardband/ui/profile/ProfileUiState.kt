package com.example.guardband.ui.profile

/** Render state for [ProfileFragment]. */
data class ProfileUiState(
    /**
     * True until the `users/{uid}` read lands.
     *
     * The other fields are already populated from the session while this is
     * true, so the screen is never blank; the read only replaces them with the
     * stored record, which is authoritative.
     */
    val isLoading: Boolean = false,

    /** Welcome-header name (already falls back to "User"). */
    val userName: String = "",
    val email: String = ""
)
