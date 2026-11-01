package com.example.guardband.ui.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.guardband.data.DeviceConstants
import com.example.guardband.data.FirebaseProvider
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.data.model.Role
import com.example.guardband.data.repository.AlertRepository
import com.example.guardband.ui.track.Clock
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * The opt-in watch: keeps the band's history listener alive while the app is
 * in the background or the phone is asleep, and posts each new alert through
 * [AlertNotifier]. A foreground service, because on the Spark plan there is
 * no server to push from; see `docs/notifications.md`.
 *
 * Started only from foreground code ([AlertWatch.start]): the Settings
 * switch, and the Home host once its gate opens. Stopped by the switch and by
 * every sign-out ([com.example.guardband.ui.auth.LoginNavigation]). There is no
 * boot receiver, so after a restart it waits for the app to be opened; that
 * keeps `RECEIVE_BOOT_COMPLETED` out of the manifest.
 *
 * It subscribes with [AlertRepository.DEFAULT_HISTORY_WINDOW], the alert
 * screens' own window, so while both run the SDK serves them from one
 * listen. Logs state changes only, never an alert, a uid or a coordinate.
 */
class AlertWatchService : Service() {

    private var scope: CoroutineScope? = null
    private val smsPanicSender by lazy { SmsPanicSender(this) }
    private var cachedRole: Role? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = AlertNotificationPrefs.from(this)
        if (!AlertWatch.shouldRun(
                enabled = prefs.watchEnabled,
                signedIn = RepositoryProvider.authRepository.isLoggedIn(),
                notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
            )
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notifier = AlertNotifier(this).apply { ensureChannels() }
        try {
            ServiceCompat.startForeground(
                this,
                AlertNotifier.WATCH_ID,
                notifier.watchNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+): a restart
            // the system would not let run in the foreground. Wait for the app.
            Log.w(TAG, "Alert watch could not start in the foreground")
            stopSelf()
            return START_NOT_STICKY
        }

        if (scope == null) {
            Log.i(TAG, "Alert watch started")
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
                watch(it, prefs, notifier)
            }
        }
        return START_STICKY
    }

    private fun watch(scope: CoroutineScope, prefs: AlertNotificationPrefs, notifier: AlertNotifier) {
        val deviceId = DeviceConstants.DEFAULT_DEVICE_ID
        var firstSinceStart = true
        scope.launch {
            RepositoryProvider.alertRepository
                .observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW)
                .collect { emission ->
                    val mark = prefs.lastNotifiedSequenceId(deviceId)
                    val plan = AlertNotificationDecider.decide(
                        mark, emission, firstSinceStart, Clock.SYSTEM.nowMillis()
                    )
                    if (emission.isSuccess) firstSinceStart = false

                    plan.toPost.forEach { alert ->
                        notifier.post(alert)
                        if (alert.alertType() == AlertType.PANIC && !alert.simulated) {
                            scope.launch {
                                if (getRole() == Role.DEPENDENT) {
                                    startLiveSessionAndSendSms(alert, deviceId)
                                }
                            }
                        }
                    }
                    notifier.postSummary(plan.summaryCount)
                    val newMark = plan.newMark
                    if (newMark != null && newMark != mark) {
                        prefs.setLastNotifiedSequenceId(deviceId, newMark)
                    }
                }
        }
    }

    private suspend fun getRole(): Role? {
        if (cachedRole != null) return cachedRole
        val uid = RepositoryProvider.authRepository.currentUser()?.id ?: return null
        val role = RepositoryProvider.userProfileRepository.fetchProfile(uid).getOrNull()?.role
        cachedRole = role
        return role
    }

    private suspend fun getGuardianPhone(): String? {
        val contacts = RepositoryProvider.contactRepository.observeContacts()
            .firstOrNull()?.getOrNull().orEmpty()
        val guardian = contacts.find { it.isEmergency }
            ?: contacts.find { it.relationship.equals("Guardian", ignoreCase = true) }
            ?: contacts.firstOrNull()
        return guardian?.phone
    }

    private suspend fun startLiveSessionAndSendSms(alert: Alert, deviceId: String) {
        val sessionId = "session_${alert.sequenceId}"
        val panicSeq = alert.sequenceId
        val sessionPayload = mapOf(
            "sessionId" to sessionId,
            "panicSeq" to panicSeq,
            "startedAt" to ServerValue.TIMESTAMP,
            "active" to true
        )
        try {
            val db = FirebaseProvider.database
            db.getReference("devices").child(deviceId).child("sessions").child(panicSeq.toString())
                .setValue(sessionPayload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record live session in RTDB", e)
        }

        val phone = getGuardianPhone()
        if (!phone.isNullOrBlank()) {
            val lat = alert.location?.lat ?: 0.0
            val lng = alert.location?.lng ?: 0.0
            smsPanicSender.sendPanicSms(phone, lat, lng, sessionId)
        } else {
            Log.w(TAG, "No guardian contact found; emergency SMS skipped.")
        }
    }

    override fun onDestroy() {
        if (scope != null) Log.i(TAG, "Alert watch stopped")
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "AlertWatch"
    }
}
