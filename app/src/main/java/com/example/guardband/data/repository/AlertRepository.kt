package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import kotlinx.coroutines.flow.Flow

/**
 * One read of the band's history window.
 *
 * [rawCount] and [malformedCount] are carried alongside the alerts so the
 * screen can tell three different silences apart: the band has sent nothing,
 * the window held only entries the Alert tab hides (D1), and the window held
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
     * May hold a `TRACKING_UPDATE`, which is why the Alert tab's latest card
     * is derived from history instead (D2). This is for the Track tab.
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

    companion object {
        /** D3's raw read window. */
        const val DEFAULT_HISTORY_WINDOW = 100
    }
}
