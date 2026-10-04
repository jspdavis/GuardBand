package com.example.guardband.ui.forgot

/** One-shot events emitted by [ForgotRequestViewModel]. */
sealed interface ForgotRequestEvent {
    data class ShowMessage(val text: String) : ForgotRequestEvent

    /**
     * The reset email was requested. Carries no email and no account status:
     * the screen must look identical whether or not the account exists.
     */
    object NavigateToSent : ForgotRequestEvent
}
