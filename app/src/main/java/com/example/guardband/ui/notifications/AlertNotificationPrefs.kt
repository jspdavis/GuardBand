package com.example.guardband.ui.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * The two things alert notifications remember on this phone: whether the
 * user turned them on, and the newest `sequenceId` already handled for a
 * device. Nothing else: no alert content, no account.
 *
 * Per phone, not per account. The band is shared until pairing exists
 * (`DeviceConstants`), so another account signing in on the same phone is
 * watching the same band.
 */
interface AlertNotificationPrefs {
    var watchEnabled: Boolean
    fun lastNotifiedSequenceId(deviceId: String): Long?
    fun setLastNotifiedSequenceId(deviceId: String, sequenceId: Long)

    companion object {
        /** The live store. `RepositoryProvider` holds no Context, so callers that have one build it. */
        fun from(context: Context): AlertNotificationPrefs =
            SharedPreferencesAlertNotificationPrefs(
                context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            )

        private const val FILE_NAME = "alert_notifications"
    }
}

class SharedPreferencesAlertNotificationPrefs(
    private val prefs: SharedPreferences
) : AlertNotificationPrefs {

    override var watchEnabled: Boolean
        get() = prefs.getBoolean(KEY_WATCH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WATCH_ENABLED, value).apply()

    override fun lastNotifiedSequenceId(deviceId: String): Long? {
        val key = KEY_MARK_PREFIX + deviceId
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    override fun setLastNotifiedSequenceId(deviceId: String, sequenceId: Long) {
        prefs.edit().putLong(KEY_MARK_PREFIX + deviceId, sequenceId).apply()
    }

    private companion object {
        const val KEY_WATCH_ENABLED = "watch_enabled"
        const val KEY_MARK_PREFIX = "last_sequence_id_"
    }
}
