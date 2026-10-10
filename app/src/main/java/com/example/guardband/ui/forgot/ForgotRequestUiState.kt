package com.example.guardband.ui.forgot

/** Render state for [ForgotRequestActivity]. */
data class ForgotRequestUiState(
    /** True while the reset request is in flight: buttons disabled, progress visible. */
    val isLoading: Boolean = false,

    /**
     * True once a request has gone out, which swaps the screen to its "check
     * your email" face.
     *
     * **This is state, not navigation.** It used to be a second Activity
     * (`ForgotSuccessActivity`), which had to go once Resend existed: resending
     * needs the address, and the only honest place to resend from is the screen
     * that still has it.
     *
     * It says nothing about whether the address is registered. It is set on
     * every outcome the request screen treats as success, which deliberately
     * includes "no such account" — see [ForgotRequestViewModel].
     */
    val sent: Boolean = false,

    /**
     * Seconds left before Resend becomes available again, or 0 when it is.
     *
     * Recomputed from the injected clock on each tick rather than counted
     * down in a field, so a tick that is late or dropped cannot leave the
     * countdown permanently wrong.
     */
    val resendSecondsRemaining: Int = 0
) {

    /** Whether the Resend button is live. */
    val canResend: Boolean
        get() = sent && !isLoading && resendSecondsRemaining == 0
}
