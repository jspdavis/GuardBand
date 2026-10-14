package com.example.guardband.ui.verify

/** One-shot events emitted by [VerifyEmailViewModel]. */
sealed interface VerifyEmailEvent {

    /**
     * Leave for the app proper, with the back stack cleared. The Activity
     * goes through Splash, which already knows whether that means Home or
     * Login - this screen does not need to decide.
     */
    object Continue : VerifyEmailEvent
}
