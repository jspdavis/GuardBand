package com.example.guardband.ui.verify

/** One-shot events emitted by [VerifyRequiredViewModel]. */
sealed interface VerifyRequiredEvent {

    data class ShowMessage(val text: String) : VerifyRequiredEvent

    /** Verified: on to Home, with the back stack cleared. */
    object ContinueToHome : VerifyRequiredEvent

    /**
     * The user chose Log Out. Goes to Login the same way Settings' Log Out
     * does, clearing the stored Google account on the way.
     */
    object LoggedOut : VerifyRequiredEvent

    /**
     * No session at all - a dead one, not a log-out - so straight to Login,
     * leaving the stored Google account alone, like the Home gate.
     */
    object NavigateToLogin : VerifyRequiredEvent

    /** "Wrong email? Delete this account": Account information, which holds Delete account. */
    object OpenAccount : VerifyRequiredEvent
}
