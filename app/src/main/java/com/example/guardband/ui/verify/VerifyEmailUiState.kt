package com.example.guardband.ui.verify

/** Where the verify-email link has got to. */
enum class VerifyEmailStage {

    /** Checking and applying the code. */
    VERIFYING,

    /** Firebase has marked the address verified. */
    VERIFIED,

    /**
     * The link cannot be used: expired, already used, or not a valid link.
     * [VerifyEmailUiState.canRetry] is true only when the cause was the
     * network, so trying again could help.
     */
    FAILED,

    /** A Firebase email link for something this app does not handle. */
    UNSUPPORTED
}

/** Render state for [VerifyEmailActivity], owned by [VerifyEmailViewModel]. */
data class VerifyEmailUiState(
    val stage: VerifyEmailStage = VerifyEmailStage.VERIFYING,

    /** What to say under the title. Never Firebase's own text. */
    val message: String? = null,

    /** True when the failure was the connection, so Retry is offered. */
    val canRetry: Boolean = false
) {
    val isBusy: Boolean get() = stage == VerifyEmailStage.VERIFYING
}
