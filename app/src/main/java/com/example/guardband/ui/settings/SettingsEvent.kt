package com.example.guardband.ui.settings

/** One-shot events emitted by [SettingsViewModel]. */
sealed interface SettingsEvent {
    data class ShowMessage(val text: String) : SettingsEvent
    object NavigateToLogin : SettingsEvent

    /**
     * Open a hosted page in the browser.
     *
     * Carries the URL rather than letting the Fragment build one, so
     * [com.example.guardband.utils.HostedPages] stays the only place a URL on
     * the Hosting domain is spelled out.
     */
    data class OpenUrl(val url: String) : SettingsEvent

    /** Open Account information, which holds Delete account. */
    object OpenAccount : SettingsEvent

    /** Open the in-app Changelog. */
    object OpenChangelog : SettingsEvent

    /** Show the system's notification permission prompt (API 33+). */
    object RequestNotificationPermission : SettingsEvent

    /** Start the alert watch service. */
    object StartAlertWatch : SettingsEvent

    /** Stop the alert watch service. */
    object StopAlertWatch : SettingsEvent

    /** Open the system page for this app's notifications. */
    object OpenNotificationSettings : SettingsEvent

    /** Open the system's battery optimisation list. */
    object OpenBatterySettings : SettingsEvent
}
