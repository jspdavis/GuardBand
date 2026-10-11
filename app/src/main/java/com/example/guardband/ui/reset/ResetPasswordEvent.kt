package com.example.guardband.ui.reset

/** One-shot events emitted by [ResetPasswordViewModel]. */
sealed interface ResetPasswordEvent {

    data class ShowMessage(val text: String) : ResetPasswordEvent

    /**
     * The password is changed. Go to Login with the back stack cleared and say
     * so.
     *
     * Carries no email and no password. The user signs in with what they just
     * chose, so nothing needs to be handed forward — and a credential in an
     * Intent extra is exactly what this flow avoids.
     */
    object NavigateToLoginWithSuccess : ResetPasswordEvent

    /**
     * The link is unusable. Go back to the request screen so a new email can
     * be sent.
     */
    object NavigateToRequest : ResetPasswordEvent
}
