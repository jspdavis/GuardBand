package com.example.guardband.ui.notifications

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.guardband.data.RepositoryProvider

/**
 * Starts and stops [AlertWatchService]. The only way in, so the conditions
 * live in one place ([shouldRun]).
 *
 * Call [start] from foreground code only: Android 12 and later refuse to
 * start a foreground service from the background.
 */
object AlertWatch {

    /** The switch is on, someone is signed in, and the phone lets the app post. */
    fun shouldRun(enabled: Boolean, signedIn: Boolean, notificationsAllowed: Boolean): Boolean =
        enabled && signedIn && notificationsAllowed

    /** Starts the watch if [shouldRun]; the Home host calls this once its gate opens. */
    fun startIfEnabled(context: Context) {
        val run = shouldRun(
            enabled = AlertNotificationPrefs.from(context).watchEnabled,
            signedIn = RepositoryProvider.authRepository.isLoggedIn(),
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
        if (run) start(context)
    }

    fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(context, intent(context))
        } catch (e: IllegalStateException) {
            // Started from the background after all; the next foreground
            // visit to Home tries again.
        }
    }

    fun stop(context: Context) {
        context.stopService(intent(context))
    }

    private fun intent(context: Context) = Intent(context, AlertWatchService::class.java)
}
