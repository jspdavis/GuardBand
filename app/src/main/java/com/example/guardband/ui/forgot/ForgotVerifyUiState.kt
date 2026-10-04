package com.example.guardband.ui.forgot

/** Render state for [ForgotVerifyActivity]. */
data class ForgotVerifyUiState(
    /**
     * True while a verify or resend request is in flight: verify button and
     * resend link disabled, progress visible.
     */
    val isLoading: Boolean = false
)
