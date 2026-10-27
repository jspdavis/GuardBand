package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.CheckIn
import kotlinx.coroutines.flow.Flow

/**
 * One read of the band's history window.
 *
 * [rawCount] and [malformedCount] are carried alongside the alerts so the
 * screen can tell three different silences apart: the band has sent nothing,
 * the window held only entries the alert screens hide (D1), and the window held
 * rows that could not be parsed. Without them an empty list is ambiguous, and
 * a tracking-heavy band would show "no incidents" while working perfectly.
 *
 * @param alerts         parsed alerts, newest first, unfiltered (D1 is applied
 *                       by the ViewModel, not here).
 * @param rawCount       children actually read, before parsing or filtering.
 * @param malformedCount entries skipped because [AlertParser] rejected them.
 */
data class AlertHistory(
    val alerts: List<Alert> = emptyList(),
    val rawCount: Int = 0,
    val malformedCount: Int = 0
)

/**
 * Alerts written by the paired band, read live.
 *
 * The device id is fixed inside the implementation (see
 * [com.example.guardband.data.DeviceConstants]) so pairing can replace it
 * later without touching ViewModels.
 *
 * Both flows emit once the first read completes and again on every change.
 * A failed read is emitted as `Result.failure(`[AlertError]`)` rather than
 * being swallowed into null or an empty list, and the flow stays open: the
 * listener is still attached, so a reconnect or a rules fix can make the next
 * emission succeed. Collect them from the main thread (e.g. viewModelScope).
 */
interface AlertRepository {

    /**
     * `/devices/{id}/latest`. Emits null while the device has no alerts.
     *
     * May hold a `TRACKING_UPDATE`, which is why the Alert history screen's
     * latest card is derived from history instead (D2). This is for the Track tab.
     */
    fun observeLatestAlert(): Flow<Result<Alert?>>

    /**
     * `/devices/{id}/history`, newest first (highest sequenceId first).
     *
     * @param limit how many of the most recent raw entries to read, before
     *   tracking updates are dropped (D3). Note this is a window over *raw*
     *   entries, so a band sending frequent tracking updates yields fewer
     *   displayable alerts than [limit] suggests.
     */
    fun observeAlertHistory(limit: Int = DEFAULT_HISTORY_WINDOW): Flow<Result<AlertHistory>>

    /**
     * The wearer's latest check-in (FR-10): the newest `CHECKIN` in the
     * [DEFAULT_HISTORY_WINDOW] history window, or null when the window holds
     * none. Read-only, like everything here: check-ins come from the band.
     *
     * Not `latest`, because every later payload overwrites that node, and not
     * a separate node, because SCHEMA.md has none. The cost is the window: a
     * check-in older than the last [DEFAULT_HISTORY_WINDOW] raw entries reads
     * as null, so "none" means "none recently", not "never".
     */
    fun observeLatestCheckIn(): Flow<Result<CheckIn?>>

    /**
     * An Incident view's read: `/devices/{id}/history` **from** the panic
     * numbered [panicSequenceId] onwards, the oldest [limit] raw entries,
     * tracking updates included, newest first like [observeAlertHistory].
     *
     * Its own read rather than [observeAlertHistory]'s window, because that
     * window is the newest [DEFAULT_HISTORY_WINDOW] entries: a band tracking
     * every few seconds pushes a panic, and the path behind it, out of it
     * within minutes. This one starts at the panic, so the whole path is there
     * however much has been sent since. Nothing it returns reaches a list: the
     * Incident view is its only reader.
     *
     * @param limit how many raw entries to read from the panic on.
     */
    fun observeIncident(
        panicSequenceId: Long,
        limit: Int = INCIDENT_WINDOW
    ): Flow<Result<AlertHistory>>

    companion object {
        /** D3's raw read window. */
        const val DEFAULT_HISTORY_WINDOW = 100

        /**
         * How many raw entries an Incident view reads from its panic on: 30
         * minutes of the mock sender's 5 s walk is 360, FR-09's real 60 s
         * tracking is 30, and the rest is room for other alerts in between.
         * About 80 KB at most.
         */
        const val INCIDENT_WINDOW = 400
    }
}
