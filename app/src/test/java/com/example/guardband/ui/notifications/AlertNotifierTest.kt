package com.example.guardband.ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.ui.home.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * What the notifications say and how loud they are: the channels, the
 * lock-screen rule (a public version with nothing in it), no photo, no
 * coordinates, and a tap that goes to the Home host.
 */
@RunWith(RobolectricTestRunner::class)
class AlertNotifierTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val system = context.getSystemService(NotificationManager::class.java)
    private val notifier = AlertNotifier(context)

    @Before
    fun createChannels() {
        notifier.ensureChannels()
    }

    private fun alert(
        type: AlertType,
        seq: Long = 7,
        battery: Alert.Battery? = Alert.Battery(percent = 18, isCharging = false)
    ) = Alert(
        schemaVersion = "1.0",
        deviceId = "guardband-001",
        type = type.name,
        timestamp = "2026-10-19T13:05:00Z",
        location = Alert.Location(lat = 10.31570, lng = 123.88540, accuracyMeters = 8.0),
        battery = battery,
        sequenceId = seq
    )

    private fun posted(seq: Long = 7): Notification =
        checkNotNull(shadowOf(system).getNotification(AlertNotifier.TAG_ALERT, AlertNotifier.notificationId(seq)))

    private fun Notification.words(): String =
        listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)
            .joinToString(" ") { extras.getCharSequence(it)?.toString().orEmpty() }

    // ── Channels ──────────────────────────────────────────────────────────────

    @Test
    fun `the five channels exist with their importance`() {
        val expected = mapOf(
            AlertNotifier.CHANNEL_PANIC to NotificationManager.IMPORTANCE_DEFAULT,
            AlertNotifier.CHANNEL_BATTERY to NotificationManager.IMPORTANCE_DEFAULT,
            AlertNotifier.CHANNEL_CHECKIN to NotificationManager.IMPORTANCE_LOW,
            AlertNotifier.CHANNEL_OTHER to NotificationManager.IMPORTANCE_DEFAULT,
            AlertNotifier.CHANNEL_WATCH to NotificationManager.IMPORTANCE_LOW
        )
        expected.forEach { (id, importance) ->
            val channel = assertNotNullChannel(id)
            assertEquals(id, importance, channel.importance)
        }
    }

    /** Discretion: the wearer may be next to the person they are afraid of. */
    @Test
    fun `the panic channel makes no sound and does not pop up`() {
        val panic = assertNotNullChannel(AlertNotifier.CHANNEL_PANIC)

        assertNull(panic.sound)
        assertTrue(panic.importance < NotificationManager.IMPORTANCE_HIGH)
        assertTrue(panic.shouldVibrate())
    }

    private fun assertNotNullChannel(id: String) =
        checkNotNull(system.getNotificationChannel(id)) { "missing channel $id" }

    // ── Content ───────────────────────────────────────────────────────────────

    @Test
    fun `each type goes to its own channel with its own title`() {
        val cases = mapOf(
            AlertType.PANIC to (AlertNotifier.CHANNEL_PANIC to "Panic alert sent"),
            AlertType.LOW_BATTERY to (AlertNotifier.CHANNEL_BATTERY to "Band battery low"),
            AlertType.CHECKIN to (AlertNotifier.CHANNEL_CHECKIN to "Check-in received")
        )
        cases.entries.forEachIndexed { i, (type, expected) ->
            notifier.post(alert(type, seq = i + 1L))
            val n = posted(i + 1L)
            assertEquals(expected.first, n.channelId)
            assertEquals(expected.second, n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        }
    }

    @Test
    fun `an unknown type is posted as a new band alert`() {
        notifier.post(alert(AlertType.PANIC).copy(type = "FALL_DETECTED"))

        assertEquals(AlertNotifier.CHANNEL_OTHER, posted().channelId)
        assertEquals("New band alert", posted().extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `a tracking update is never posted`() {
        notifier.post(alert(AlertType.TRACKING_UPDATE))

        assertTrue(shadowOf(system).allNotifications.isEmpty())
    }

    @Test
    fun `low battery says the percent, or that it is low when the percent is missing`() {
        notifier.post(alert(AlertType.LOW_BATTERY, seq = 1))
        notifier.post(alert(AlertType.LOW_BATTERY, seq = 2, battery = null))

        assertTrue(posted(1).words().contains("18%"))
        assertTrue(posted(2).words().contains("battery is low"))
    }

    @Test
    fun `no notification carries the band's coordinates`() {
        AlertType.entries.filter { it != AlertType.TRACKING_UPDATE }.forEachIndexed { i, type ->
            notifier.post(alert(type, seq = i + 1L))
            val words = posted(i + 1L).words()
            assertFalse(words, words.contains("10.31"))
            assertFalse(words, words.contains("123.88"))
        }
    }

    // ── Lock screen ───────────────────────────────────────────────────────────

    @Test
    fun `every alert is private, with a public version that says nothing`() {
        AlertType.entries.filter { it != AlertType.TRACKING_UPDATE }.forEachIndexed { i, type ->
            notifier.post(alert(type, seq = i + 1L))
            val n = posted(i + 1L)

            assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
            val public = checkNotNull(n.publicVersion)
            assertEquals("GuardBand New alert from your band. Unlock to see it.", public.words())
        }
    }

    @Test
    fun `no notification shows a photo or a type symbol`() {
        notifier.post(alert(AlertType.PANIC))
        val n = posted()

        assertNull(n.getLargeIcon())
        assertEquals(n.smallIcon.resId, checkNotNull(n.publicVersion).smallIcon.resId)
        assertEquals(com.example.guardband.R.drawable.ic_stat_alert, n.smallIcon.resId)
    }

    // ── Tap ───────────────────────────────────────────────────────────────────

    @Test
    fun `a tap opens the home host through an immutable intent`() {
        notifier.post(alert(AlertType.PANIC))
        val intent = checkNotNull(posted().contentIntent)
        val shadow = shadowOf(intent)

        assertTrue(shadow.isActivity)
        assertEquals(HomeActivity::class.java.name, shadow.savedIntent.component?.className)
        assertTrue(shadow.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertTrue(posted().flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    // ── Summary and watch ─────────────────────────────────────────────────────

    @Test
    fun `the summary counts the rest`() {
        notifier.postSummary(2)

        val n = checkNotNull(shadowOf(system).getNotification(AlertNotifier.TAG_SUMMARY, AlertNotifier.SUMMARY_ID))
        assertEquals("GuardBand 2 more alerts from your band.", n.words())
        assertNotNull(n.publicVersion)
    }

    @Test
    fun `no summary for zero`() {
        notifier.postSummary(0)

        assertTrue(shadowOf(system).allNotifications.isEmpty())
    }

    @Test
    fun `the watch notification is ongoing and quiet`() {
        val n = notifier.watchNotification()

        assertEquals(AlertNotifier.CHANNEL_WATCH, n.channelId)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("GuardBand Watching your band for alerts", n.words())
    }

    @Test
    fun `nothing is posted while the phone blocks the app's notifications`() {
        shadowOf(system).setNotificationsEnabled(false)

        notifier.post(alert(AlertType.PANIC))

        assertTrue(shadowOf(system).allNotifications.isEmpty())
    }
}
