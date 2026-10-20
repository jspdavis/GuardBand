package com.example.guardband.ui.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.ui.alert.AlertFormatting
import com.example.guardband.ui.home.HomeActivity
import com.example.guardband.ui.home.HomeLaunchTarget
import com.example.guardband.ui.track.BandStatus

/**
 * Posts GuardBand's notifications: one per band alert, the summary, and the
 * watch service's ongoing one. The thin Android layer; which alerts to post
 * is [AlertNotificationDecider]'s.
 *
 * **What a notification may say:** the alert's type, its time and the
 * band's battery. Never coordinates, an address, a map link, a name, the
 * profile photo, the device id or an email. Every alert is
 * `VISIBILITY_PRIVATE` with a public version that says only that there is a
 * new alert, and all of them share one status-bar icon, so a lock screen set
 * to hide sensitive content says nothing about what happened.
 *
 * **Channels belong to the user once created:** an app cannot change a
 * channel's importance or sound afterwards, only a new channel id can. The
 * panic channel is silent on purpose: the wearer pressed it, perhaps with
 * someone nearby, and the phone must not ring or show a heads-up banner.
 * Main thread only ([AlertFormatting]).
 */
class AlertNotifier(context: Context) {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** Creates the channels. Safe to call every time: re-creating changes nothing. */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannels(
            listOf(
                channel(CHANNEL_PANIC, R.string.notif_channel_panic, NotificationManager.IMPORTANCE_DEFAULT) {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = PANIC_VIBRATION
                },
                channel(CHANNEL_BATTERY, R.string.notif_channel_battery, NotificationManager.IMPORTANCE_DEFAULT),
                channel(CHANNEL_CHECKIN, R.string.notif_channel_checkin, NotificationManager.IMPORTANCE_LOW),
                channel(CHANNEL_OTHER, R.string.notif_channel_other, NotificationManager.IMPORTANCE_DEFAULT),
                channel(CHANNEL_WATCH, R.string.notif_channel_watch, NotificationManager.IMPORTANCE_LOW) {
                    setShowBadge(false)
                }
            )
        )
    }

    /** Posts [alert]. Does nothing for a tracking update, or when notifications are off. */
    fun post(alert: Alert) {
        val type = alert.alertType()
        if (type == AlertType.TRACKING_UPDATE) return
        val time = AlertFormatting.formatTimestamp(alert.timestamp)

        val (title, text) = when (type) {
            AlertType.PANIC ->
                string(R.string.notif_panic_title) to string(R.string.notif_panic_text, time)
            AlertType.LOW_BATTERY ->
                string(R.string.notif_battery_title) to (
                    alert.battery?.let { string(R.string.notif_battery_text, it.percent) }
                        ?: string(R.string.notif_battery_text_unknown)
                    )
            AlertType.CHECKIN ->
                string(R.string.notif_checkin_title) to string(R.string.notif_checkin_text, time)
            else ->
                string(R.string.notif_other_title) to string(R.string.notif_other_text, time)
        }

        val notification = alertBuilder(channelFor(type))
            .setContentTitle(title)
            .setContentText(text)
            .setColor(ContextCompat.getColor(context, AlertFormatting.typeColor(type)))
            .setCategory(
                if (type == AlertType.CHECKIN) NotificationCompat.CATEGORY_STATUS
                else NotificationCompat.CATEGORY_ERROR
            )
            .setWhen(BandStatus.parseIsoUtcMillis(alert.timestamp) ?: System.currentTimeMillis())
            .setShowWhen(true)
            .applyPreOreoSound(type)
            .build()

        notify(TAG_ALERT, notificationId(alert.sequenceId), notification)
    }

    /** The alerts beyond the per-emission cap, as one notification. */
    fun postSummary(count: Int) {
        if (count <= 0) return
        val notification = alertBuilder(CHANNEL_OTHER)
            .setContentTitle(string(R.string.notif_public_title))
            .setContentText(
                context.resources.getQuantityString(R.plurals.notif_summary_text, count, count)
            )
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        notify(TAG_SUMMARY, SUMMARY_ID, notification)
    }

    /** The watch service's ongoing notification. Tapping it opens Alert notifications, where it is turned off. */
    fun watchNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(string(R.string.notif_watch_title))
            .setContentText(string(R.string.notif_watch_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openHome(HomeLaunchTarget.ALERT_NOTIFICATIONS, REQUEST_ALERT_NOTIFICATIONS))
            .build()

    private fun alertBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channelId))
            .setContentIntent(openHome(HomeLaunchTarget.ALERT_HISTORY, REQUEST_ALERT_HISTORY))
            .setAutoCancel(true)

    /** What a lock screen hiding sensitive content shows instead: no type, time or battery. */
    private fun publicVersion(channelId: String): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(string(R.string.notif_public_title))
            .setContentText(string(R.string.notif_public_text))
            .build()

    /**
     * Below API 26 there are no channels, so the sound is set per
     * notification. The panic gets none and no vibration either: vibrating
     * there can need the VIBRATE permission, and the app asks for no more
     * permissions than it must.
     */
    private fun NotificationCompat.Builder.applyPreOreoSound(type: AlertType?) = apply {
        // From API 26 the channel decides, and setSilent would also stop the
        // panic channel's vibration.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return@apply
        when (type) {
            AlertType.PANIC -> setSilent(true)
            AlertType.CHECKIN -> setPriority(NotificationCompat.PRIORITY_LOW)
            else -> setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
    }

    private fun openHome(target: HomeLaunchTarget, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            HomeActivity.launchIntent(context, target),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Posting without the permission is refused by the system; check rather than let it throw. */
    @SuppressLint("MissingPermission")
    private fun notify(tag: String, id: Int, notification: Notification) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(tag, id, notification)
        } catch (e: SecurityException) {
            // Revoked between the check and the post. Nothing to tell anyone.
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private inline fun channel(
        id: String,
        nameRes: Int,
        importance: Int,
        configure: NotificationChannel.() -> Unit = {}
    ): NotificationChannel =
        NotificationChannel(id, string(nameRes), importance).apply(configure)

    private fun string(res: Int, vararg args: Any): String = context.getString(res, *args)

    private fun channelFor(type: AlertType?): String =
        when (type) {
            AlertType.PANIC -> CHANNEL_PANIC
            AlertType.LOW_BATTERY -> CHANNEL_BATTERY
            AlertType.CHECKIN -> CHANNEL_CHECKIN
            else -> CHANNEL_OTHER
        }

    companion object {
        const val CHANNEL_PANIC = "alert_panic"
        const val CHANNEL_BATTERY = "alert_battery"
        const val CHANNEL_CHECKIN = "alert_checkin"
        const val CHANNEL_OTHER = "alert_other"
        const val CHANNEL_WATCH = "alert_watch"

        const val TAG_ALERT = "alert"
        const val TAG_SUMMARY = "alert_summary"
        const val SUMMARY_ID = 1

        /** The watch service's notification id (`startForeground`). */
        const val WATCH_ID = 1001

        private const val REQUEST_ALERT_HISTORY = 1
        private const val REQUEST_ALERT_NOTIFICATIONS = 2

        /** One short buzz. */
        private val PANIC_VIBRATION = longArrayOf(0L, 200L)

        /** One notification per alert; ids repeat only past Int.MAX_VALUE alerts. */
        fun notificationId(sequenceId: Long): Int = (sequenceId % Int.MAX_VALUE).toInt()
    }
}
