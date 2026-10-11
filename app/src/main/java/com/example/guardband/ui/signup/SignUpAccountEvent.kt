package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpAccountViewModel]. */
sealed interface SignUpAccountEvent {
    data class ShowMessage(val text: String) : SignUpAccountEvent

    /**
     * Carries the credentials to the next step.
     *
     * They are only *collected* here. No Firebase account exists until the
     * Consent screen submits, so abandoning the wizard leaves nothing behind
     * and a half-finished sign-up can never occupy an address.
     */
    data class NavigateToName(val email: String, val password: String) : SignUpAccountEvent
}
