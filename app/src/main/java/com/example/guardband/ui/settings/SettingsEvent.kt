package com.example.guardband.ui.settings

/** One-shot events emitted by [SettingsViewModel]. */
sealed interface SettingsEvent {
    data class ShowMessage(val text: String) : SettingsEvent
    object NavigateToLogin : SettingsEvent

    /** Open Account information, which holds Delete account. */
    object OpenAccount : SettingsEvent

    /** Open Change password. Only sent for a password account. */
    object OpenChangePassword : SettingsEvent

    /** Open the in-app Changelog. */
    object OpenChangelog : SettingsEvent

    /** Open Alert notifications, which holds the switch for the alert watch. */
    object OpenAlertNotifications : SettingsEvent

    /** Open Display, which holds the theme. */
    object OpenDisplay : SettingsEvent

    /** Open About, which holds the version and the Privacy Notice. */
    object OpenAbout : SettingsEvent
}