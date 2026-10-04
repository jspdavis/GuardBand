package com.example.guardband.ui.forgot

/** Render state for [ForgotRequestActivity]. */
data class ForgotRequestUiState(
    /** True while the reset request is in flight: button disabled, progress visible. */
    val isLoading: Boolean = false
)
