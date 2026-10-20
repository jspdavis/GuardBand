package com.example.guardband.ui.notifications

/** One-shot events emitted by [AlertNotificationsViewModel]. */
sealed interface AlertNotificationsEvent {
    data class ShowMessage(val text: String) : AlertNotificationsEvent

    /** Show the system's notification permission prompt (API 33+). */
    object RequestNotificationPermission : AlertNotificationsEvent

    /** Start the alert watch service. */
    object StartAlertWatch : AlertNotificationsEvent

    /** Stop the alert watch service. */
    object StopAlertWatch : AlertNotificationsEvent

    /** Open the system page for this app's notifications. */
    object OpenNotificationSettings : AlertNotificationsEvent

    /** Open the system's battery optimisation list. */
    object OpenBatterySettings : AlertNotificationsEvent
}
