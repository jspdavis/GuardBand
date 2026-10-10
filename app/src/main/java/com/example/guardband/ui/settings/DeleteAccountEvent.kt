package com.example.guardband.ui.settings

/**
 * One-shot events emitted by [DeleteAccountViewModel].
 *
 * The three prompts are events rather than state because showing a dialog or a
 * credential sheet happens once; a failure, by contrast, is state, so it
 * survives a rotation and cannot be dismissed into nothing.
 */
sealed interface DeleteAccountEvent {

    data class ShowMessage(val text: String) : DeleteAccountEvent

    /**
     * Put up the confirmation modal, which spells out what is about to be
     * deleted. Nothing has been written when this is sent.
     */
    object ShowConfirmation : DeleteAccountEvent

    /** Ask for the current password. Only for a [com.example.guardband.data.model.SignInProvider.PASSWORD] account. */
    object AskForPassword : DeleteAccountEvent

    /**
     * Open the Google chooser and send the token back through
     * [DeleteAccountViewModel.onGoogleIdToken].
     *
     * Credential Manager needs an Activity, so the request itself belongs to
     * the Fragment — the same split sign-in uses.
     */
    object RequestGoogleIdToken : DeleteAccountEvent

    /**
     * The account is gone. Go to Login with the back stack cleared.
     *
     * Carries nothing: there is no account left to say anything about.
     */
    object NavigateToLogin : DeleteAccountEvent
}
