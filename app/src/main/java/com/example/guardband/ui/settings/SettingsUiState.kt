package com.example.guardband.ui.settings

/**
 * The one piece of Settings that is state: the Alert notifications switch.
 *
 * @param alertNotificationsOn the switch, and whether the battery row shows.
 *   On only while the user wants it **and** the phone lets the app post: a
 *   switch reading "on" over a blocked app would be a lie.
 * @param notificationsBlocked the phone refuses GuardBand's notifications
 *   (the prompt was refused, or they were turned off in system settings), so
 *   the help text says so and offers the system page.
 */
data class SettingsUiState(
    val alertNotificationsOn: Boolean = false,
    val notificationsBlocked: Boolean = false
)
