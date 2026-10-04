package com.example.guardband.ui.signup

/** Render state for [SignUpContactsActivity]. */
data class SignUpContactsUiState(
    /** True while registration is in flight: button disabled, progress visible. */
    val isLoading: Boolean = false
)
