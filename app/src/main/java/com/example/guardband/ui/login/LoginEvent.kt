package com.example.guardband.ui.login

/** One-shot events emitted by [LoginViewModel]. */
sealed interface LoginEvent {
    data class ShowMessage(val text: String) : LoginEvent
    object NavigateToLoadingHome : LoginEvent
    object NavigateToSignUp : LoginEvent
    object NavigateToForgotPassword : LoginEvent

    /**
     * A first-time Google user: the Firebase account exists but the profile is
     * bare, so the remaining sign-up steps run in complete-profile mode.
     *
     * [name] and [email] come from the Google account and are prefilled; there
     * is no password to collect and no account to create.
     */
    data class NavigateToCompleteProfile(val name: String, val email: String) : LoginEvent

    /** Ask the Activity to open the Google account chooser. */
    object RequestGoogleIdToken : LoginEvent
}
