package com.example.guardband.ui.track

/** Render state for [TrackFragment]. */
data class TrackUiState(
    /** User-chip name (already falls back to "User"). */
    val userName: String = "",
    /**
     * User-chip location (already falls back to "Location not set").
     *
     * Starts on that fallback and is replaced when the `users/{uid}` read
     * lands, because the session cannot supply it.
     */
    val userLocation: String = ""
)
