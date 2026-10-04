package com.example.guardband.ui.login

/** Render state for [LoginActivity]. */
data class LoginUiState(
    /** True while a login request is in flight: button disabled, progress visible. */
    val isLoading: Boolean = false
)
