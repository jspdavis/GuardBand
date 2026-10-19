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
}
