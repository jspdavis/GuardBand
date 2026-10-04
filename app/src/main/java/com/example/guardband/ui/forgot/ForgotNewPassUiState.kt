package com.example.guardband.ui.forgot

/** Render state for [ForgotNewPassActivity]. */
data class ForgotNewPassUiState(
    /** True while the password reset is in flight: button disabled, progress visible. */
    val isLoading: Boolean = false
)
