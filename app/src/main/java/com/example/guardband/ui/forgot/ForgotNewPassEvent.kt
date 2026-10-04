package com.example.guardband.ui.forgot

/** One-shot events emitted by [ForgotNewPassViewModel]. */
sealed interface ForgotNewPassEvent {
    data class ShowMessage(val text: String) : ForgotNewPassEvent
    object NavigateToSuccess : ForgotNewPassEvent
}
