package com.example.guardband.ui.forgot

/**
 * One-shot events emitted by [ForgotRequestViewModel].
 *
 * `NavigateToSent` is gone. The confirmation is the same screen in a different
 * state now ([ForgotRequestUiState.sent]), because Resend has to happen where
 * the email address still is.
 */
sealed interface ForgotRequestEvent {
    data class ShowMessage(val text: String) : ForgotRequestEvent
}
