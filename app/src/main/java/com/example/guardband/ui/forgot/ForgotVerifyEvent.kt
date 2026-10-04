package com.example.guardband.ui.forgot

/** One-shot events emitted by [ForgotVerifyViewModel]. */
sealed interface ForgotVerifyEvent {
    data class ShowMessage(val text: String) : ForgotVerifyEvent
    data class NavigateToNewPassword(val email: String) : ForgotVerifyEvent
}
