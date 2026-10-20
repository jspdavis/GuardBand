package com.example.guardband.ui.settings

/** One-shot events emitted by [SettingsViewModel]. */
sealed interface SettingsEvent {
    data class ShowMessage(val text: String) : SettingsEvent
    object NavigateToLogin : SettingsEvent

    /** Open Account information, which holds Delete account. */
    object OpenAccount : SettingsEvent

    /** Open the in-app Changelog. */
    object OpenChangelog : SettingsEvent

    /** Open Alert notifications, which holds the switch for the alert watch. */
    object OpenAlertNotifications : SettingsEvent

    /** Open About, which holds the version and the Privacy Notice. */
    object OpenAbout : SettingsEvent
}
