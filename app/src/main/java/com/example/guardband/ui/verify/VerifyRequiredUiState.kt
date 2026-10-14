package com.example.guardband.ui.verify

/** Render state for [VerifyRequiredActivity], owned by [VerifyRequiredViewModel]. */
data class VerifyRequiredUiState(
    /** The address the link was sent to: the account's own, from the session. */
    val email: String = "",

    /** Reloading the account to see whether the link has been opened. */
    val isChecking: Boolean = false,

    /** A verification email is being requested. */
    val isSending: Boolean = false,

    /** Seconds until Resend unlocks again; 0 when it is available. */
    val resendSecondsRemaining: Int = 0
) {
    val isBusy: Boolean get() = isChecking || isSending

    val resendEnabled: Boolean get() = !isBusy && resendSecondsRemaining == 0
}
