package com.example.guardband.ui.login

/** Render state for [LoginActivity]. */
data class LoginUiState(
    /** True while a login request is in flight: button disabled, progress visible. */
    val isLoading: Boolean = false,

    /**
     * True from the moment the Google button is tapped until the sign-in
     * settles, including while the account chooser is on screen.
     *
     * Separate from [isLoading] so the two buttons can be disabled together
     * while only the one that was tapped shows progress. Either flag blocks a
     * second tap on either button.
     */
    val isGoogleLoading: Boolean = false
) {
    /** True while any sign-in attempt is in flight. Guards both buttons. */
    val isBusy: Boolean get() = isLoading || isGoogleLoading
}
