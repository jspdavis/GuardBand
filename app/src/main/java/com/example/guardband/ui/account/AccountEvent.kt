package com.example.guardband.ui.account

/** One-shot events emitted by [AccountViewModel]. */
sealed interface AccountEvent {

    data class ShowMessage(val text: String) : AccountEvent

    /** Open Change password. Only sent for a password account. */
    object OpenChangePassword : AccountEvent

    /**
     * There is no session, so there is no account to show. Go to Login with
     * the back stack cleared.
     *
     * A dead session rather than a sign-out, so the Activity does **not**
     * clear the stored Google account - the same rule as
     * [HomeViewModel][com.example.guardband.ui.home.HomeViewModel]'s gate.
     */
    object NavigateToLogin : AccountEvent
}
