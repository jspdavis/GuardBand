package com.example.guardband.ui.home

/** One-shot events emitted by [HomeViewModel]. */
sealed interface HomeEvent {
    object NavigateToLogin : HomeEvent
    object ShowSettings : HomeEvent

    /**
     * Open Alert notifications over Settings, from the watch's ongoing
     * notification, so Back from it lands on Settings as it does when the
     * row is tapped.
     */
    object ShowAlertNotifications : HomeEvent
    object ShowNotifications : HomeEvent

    /** Open the full alert history, from the Track tab's recent-alerts panel. */
    object ShowAlertHistory : HomeEvent


    /**
     * This account's deletion did not finish: its data is gone and its sign-in
     * is not. Leave the tabs for Account information, which offers to finish.
     */
    object ResumeDeletion : HomeEvent

    /**
     * A password account whose email is not verified yet. Leave the tabs for
     * the verification gate; nothing in the app is usable until it clears.
     */
    object RequireEmailVerification : HomeEvent

    /**
     * The account has no consent record (a Console-made account, or a Google
     * account whose record went missing). Leave for the consent screen; the
     * tabs are never shown to it.
     */
    object RequireConsent : HomeEvent
}
