package com.example.guardband.ui.consent

/** One-shot events emitted by [ConsentRequiredViewModel]. */
sealed interface ConsentRequiredEvent {
    data class ShowMessage(val text: String) : ConsentRequiredEvent

    /** Open the Privacy Notice in the browser. Carries the URL, as on sign-up. */
    data class OpenPrivacyNotice(val url: String) : ConsentRequiredEvent

    /**
     * Consent is stored. Back to the Home host with the task cleared; its gate
     * reads the record again and opens.
     */
    object ContinueToHome : ConsentRequiredEvent

    /**
     * The user declined and has been signed out of Firebase. Show [message],
     * forget the Google account, and go to Login with the task cleared.
     */
    data class Declined(val message: String) : ConsentRequiredEvent

    /** The session ended while the screen was open. Go to Login. */
    object NavigateToLogin : ConsentRequiredEvent
}
