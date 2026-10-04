package com.example.guardband.ui.forgot

/** One-shot events emitted by [ForgotRequestViewModel]. */
sealed interface ForgotRequestEvent {
    data class ShowMessage(val text: String) : ForgotRequestEvent
    data class NavigateToVerify(val email: String) : ForgotRequestEvent
}
