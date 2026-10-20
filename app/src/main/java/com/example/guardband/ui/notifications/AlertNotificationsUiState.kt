package com.example.guardband.ui.notifications

/**
 * The Alert notifications screen's state: its switch.
 *
 * @param alertNotificationsOn the switch, and whether the battery row shows.
 *   On only while the user wants it **and** the phone lets the app post: a
 *   switch reading "on" over a blocked app would be a lie.
 * @param notificationsBlocked the phone refuses GuardBand's notifications
 *   (the prompt was refused, or they were turned off in system settings), so
 *   the help text says so and offers the system page.
 */
data class AlertNotificationsUiState(
    val alertNotificationsOn: Boolean = false,
    val notificationsBlocked: Boolean = false
)
