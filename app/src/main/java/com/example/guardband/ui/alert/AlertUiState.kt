package com.example.guardband.ui.alert

import com.example.guardband.data.model.Alert
import com.example.guardband.ui.track.BandStatus
import java.util.TimeZone

/**
 * Render state for [AlertHistoryFragment] and Track's recent-alerts panel
 * ([com.example.guardband.ui.track.RecentAlertsPanel]).
 *
 * Four situations in one class: loading, content, empty and error. They are
 * distinguished by which fields are set rather than by a sealed hierarchy,
 * because the Fragment needs to keep showing the last good list while a
 * refresh fails - a sealed state would have to throw that list away to become
 * an error.
 */
data class AlertUiState(
    /** True until the first read of the history window completes. */
    val isLoading: Boolean = true,

    /**
     * The band's most recent **non-tracking** alert (D2), shown in the latest
     * card. Derived from history, not from `devices/{id}/latest`, which may
     * hold a TRACKING_UPDATE.
     */
    val latest: Alert? = null,

    /**
     * Incident history (FR-08), newest first: tracking updates removed (D1)
     * and capped at [AlertViewModel.MAX_HISTORY] (D3).
     */
    val history: List<Alert> = emptyList(),

    /**
     * The newest [AlertViewModel.RECENT_ALERTS_LIMIT] entries of [history], for
     * the Track tab's recent-alerts panel. A slice of the same list rather
     * than a second read, so the panel and the history screen can never
     * disagree.
     */
    val recent: List<Alert> = emptyList(),

    /**
     * Non-null when the last read failed **and** there is nothing on screen to
     * fall back to. Shows the error view and its Retry button.
     *
     * A failure that arrives while content is already displayed does not set
     * this; it goes out as [AlertEvent.ShowMessage] instead, so the user is
     * told the list is stale without losing it.
     */
    val errorMessage: String? = null,

    /**
     * Non-null when the read succeeded but there is nothing to display, and
     * says *why* - the band has sent nothing, the window held only tracking
     * updates, or nothing in it could be read.
     */
    val emptyMessage: String? = null,

    /**
     * Place names found so far, by [AlertPlaces.keyOf] the alert's location.
     * Fills in after the list shows; an alert with no entry yet (or none to
     * be found) shows its coordinates.
     */
    val placeNames: Map<String, String> = emptyMap(),

    /**
     * The clock when the list was last read or last ticked
     * ([AlertViewModel.TICK_INTERVAL_MS]): what "5 min ago" and "Today" are
     * measured from, so both move on while the list stays the same.
     */
    val nowMillis: Long = 0L
) {
    /** [alert]'s place name, once known. */
    fun placeNameFor(alert: Alert): String? =
        alert.location?.let { placeNames[AlertPlaces.keyOf(it)] }

    /** [alerts] as rows, each with its place name if known and how long ago it was. */
    fun rowsOf(alerts: List<Alert>): List<AlertRow> = alerts.map { alert ->
        AlertRow(
            alert,
            placeNameFor(alert),
            BandStatus.parseIsoUtcMillis(alert.timestamp)
                ?.let { BandStatus.elapsedSince(it, nowMillis) }
        )
    }

    /** [history] under a heading for each day, as the Alert history screen lists it. */
    fun historyItems(zone: TimeZone = TimeZone.getDefault()): List<AlertListItem> =
        AlertDays.group(rowsOf(history), nowMillis, zone)
}
