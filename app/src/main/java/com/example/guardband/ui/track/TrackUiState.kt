package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert

/**
 * Render state for [TrackFragment].
 *
 * Four situations in one class - loading, a fix, no fix, and error -
 * distinguished by which fields are set rather than by a sealed hierarchy, for
 * the same reason [AlertUiState][com.example.guardband.ui.alert.AlertUiState]
 * does it: the screen keeps showing the last known position while a later read
 * fails, and a sealed state would have to throw that position away to become
 * an error.
 */
data class TrackUiState(
    /** User-chip name (already falls back to "User"). */
    val userName: String = "",

    /** True until the first read of `devices/{id}/latest` completes. */
    val isLoading: Boolean = true,

    /**
     * True once the band has been seen reporting at all. False means the
     * device has never written `latest` - which is a different thing from
     * having reported without a GPS fix, and reads differently on screen.
     */
    val hasReported: Boolean = false,

    /**
     * Last **known** position, retained across reports that carry none (D3).
     * Null until the band has ever sent a fix.
     */
    val location: Alert.Location? = null,

    /** Last known battery reading, retained on the same rule as [location]. */
    val battery: Alert.Battery? = null,

    /**
     * Whether the band reported recently enough to count as online
     * ([BandStatus.ONLINE_THRESHOLD_MS]). Recomputed on a timer as well as on
     * each report, because going quiet produces no new data to react to.
     */
    val isOnline: Boolean = false,

    /** How long ago the band last reported, for the "last seen" line. */
    val elapsed: Elapsed? = null,

    /**
     * Non-null when the last read failed **and** there is no position on screen
     * to fall back to. A failure that arrives while a position is displayed
     * goes out as [TrackEvent.ShowMessage] instead, so the user is told it is
     * stale without losing it.
     */
    val errorMessage: String? = null
) {
    /** Whether there is a position to put a marker on (D3, D4). */
    val hasFix: Boolean get() = location != null

    /** D6: Navigate is meaningless without somewhere to navigate to. */
    val navigateEnabled: Boolean get() = location != null
}
