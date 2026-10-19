package com.example.guardband.ui.notifications

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.ui.track.BandStatus

/**
 * What one history emission should post, and the high-water mark to store
 * after it.
 *
 * @param toPost       alerts to post one by one, newest first, at most
 *                     [AlertNotificationDecider.MAX_POSTS_PER_EMISSION].
 * @param summaryCount new alerts beyond [toPost] that get one summary instead.
 * @param newMark      the `sequenceId` to store, or null to leave the mark unset.
 */
data class AlertNotificationPlan(
    val toPost: List<Alert> = emptyList(),
    val summaryCount: Int = 0,
    val newMark: Long? = null
)

/**
 * Decides which alerts in a history emission are new enough to notify.
 *
 * Plain Kotlin, so every rule is a JVM test. The service feeds it each
 * emission with the mark it stored last time; the rules are in
 * `docs/notifications.md` §3:
 *
 * 1. **No mark yet** (first watch on this phone): store the newest
 *    `sequenceId` and post nothing, so an old history is never dumped onto
 *    the shade. An empty history stores 0, so the band's very first alert
 *    is still posted.
 * 2. **New** means a `sequenceId` above the mark.
 * 3. **The first emission after a (re)start** posts only alerts from the
 *    last [MISSED_ALERT_WINDOW_MS]; older ones, and unreadable timestamps,
 *    are absorbed. A future timestamp counts as recent: the band's clock can
 *    run ahead of the phone's.
 * 4. **A history reset** (the newest below the mark) re-baselines and posts
 *    nothing.
 * 5. `TRACKING_UPDATE` is never posted, but still advances the mark. An
 *    unrecognised type is posted (D1: shown, not hidden).
 * 6. At most [MAX_POSTS_PER_EMISSION] each time, newest first; the rest are
 *    one summary.
 * 7. A failed read changes nothing.
 */
object AlertNotificationDecider {

    /** FR-09's post-alert tracking window: 30 minutes. */
    const val MISSED_ALERT_WINDOW_MS = 30 * 60 * 1000L

    const val MAX_POSTS_PER_EMISSION = 3

    fun decide(
        mark: Long?,
        emission: Result<AlertHistory>,
        firstSinceStart: Boolean,
        nowMillis: Long
    ): AlertNotificationPlan {
        val history = emission.getOrElse { return AlertNotificationPlan(newMark = mark) }
        val newest = history.alerts.maxOfOrNull { it.sequenceId } ?: 0L

        if (mark == null || newest < mark) return AlertNotificationPlan(newMark = newest)

        val fresh = history.alerts
            .filter { it.sequenceId > mark && it.alertType() != AlertType.TRACKING_UPDATE }
            .filter { !firstSinceStart || isRecent(it, nowMillis) }
            .sortedByDescending { it.sequenceId }

        return AlertNotificationPlan(
            toPost = fresh.take(MAX_POSTS_PER_EMISSION),
            summaryCount = (fresh.size - MAX_POSTS_PER_EMISSION).coerceAtLeast(0),
            newMark = newest
        )
    }

    private fun isRecent(alert: Alert, nowMillis: Long): Boolean {
        val sent = BandStatus.parseIsoUtcMillis(alert.timestamp) ?: return false
        return nowMillis - sent <= MISSED_ALERT_WINDOW_MS
    }
}
