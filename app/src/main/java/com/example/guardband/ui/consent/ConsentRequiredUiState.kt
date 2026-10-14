package com.example.guardband.ui.consent

/** Render state for [ConsentRequiredActivity]. */
data class ConsentRequiredUiState(
    /** The checkbox. Agree stays disabled until this is true. */
    val consentGiven: Boolean = false,

    /** True while the consent write is in flight: buttons disabled, progress visible. */
    val isSaving: Boolean = false,

    /**
     * True after a write failed, so the screen shows Retry in Agree's place.
     * Retry is the same call; nothing is continued without a stored record.
     */
    val saveFailed: Boolean = false
)
