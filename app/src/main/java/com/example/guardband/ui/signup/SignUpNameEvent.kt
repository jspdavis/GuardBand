package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpNameViewModel]. */
sealed interface SignUpNameEvent {
    data class ShowMessage(val text: String) : SignUpNameEvent

    /**
     * Carries everything collected so far to the contacts step.
     *
     * [name] is the first and last name joined, because that is the single
     * field the account and the profile record both store. The credentials are
     * passed straight through from step 1 — this screen neither reads nor
     * validates them.
     */
    data class NavigateToContacts(
        val name: String,
        val email: String,
        val password: String
    ) : SignUpNameEvent

    /**
     * Hand the user to Login to sign in with Google.
     *
     * Sign-up does not run the Google flow itself: Login already owns it, and
     * duplicating it here would mean two screens creating accounts. An existing
     * Google user who taps this is simply signed in, which is what they wanted.
     */
    object NavigateToGoogleSignIn : SignUpNameEvent
}
