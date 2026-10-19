package com.example.guardband.testing

import com.example.guardband.ui.notifications.AlertNotificationPrefs

/** In-memory [AlertNotificationPrefs]: the switch and the marks, nothing on disk. */
class FakeAlertNotificationPrefs(
    override var watchEnabled: Boolean = false
) : AlertNotificationPrefs {

    val marks = mutableMapOf<String, Long>()

    override fun lastNotifiedSequenceId(deviceId: String): Long? = marks[deviceId]

    override fun setLastNotifiedSequenceId(deviceId: String, sequenceId: Long) {
        marks[deviceId] = sequenceId
    }
}
