package com.example.guardband.ui.incident

import com.example.guardband.data.model.Alert
import com.example.guardband.ui.track.BandStatus
import com.example.guardband.ui.track.Elapsed
import com.example.guardband.ui.track.MapLayer

/**
 * Render state for [IncidentFragment]: one panic, the path after it, and the
 * summary under the map.
 *
 * Loading, content and error are told apart by which fields are set, as in
 * [AlertUiState][com.example.guardband.ui.alert.AlertUiState], so a failed
 * refresh keeps the path on screen.
 */
data class IncidentUiState(
    /** True until the first read completes. */
    val isLoading: Boolean = true,

    /** The panic and its tracking updates, once read. */
    val incident: Incident? = null,

    /**
     * Non-null when there is nothing to show and why: the read failed with
     * nothing on screen, or the panic is no longer in the band's history.
     */
    val errorMessage: String? = null,

    /** The panic point's place name, once the geocoder answers; else coordinates show. */
    val placeName: String? = null,

    /** The base map, Track's own choice ([com.example.guardband.ui.track.MapLayerPrefs]). */
    val layer: MapLayer = MapLayer.AUTO,

    /** The clock at the last read or tick: what "5 min ago" is measured from. */
    val nowMillis: Long = 0L
) {
    /** How long ago the panic was; null before it is read or when its time is unreadable. */
    val panicElapsed: Elapsed? get() = incident?.panic?.let(::elapsedOf)

    /** How many tracking updates followed the panic. */
    val updateCount: Int get() = incident?.trackingUpdates?.size ?: 0

    /** The newest tracking update, or null when none has arrived. */
    val latestUpdate: Alert? get() = incident?.latestUpdate

    /** How long ago [latestUpdate] was. */
    val latestUpdateElapsed: Elapsed? get() = latestUpdate?.let(::elapsedOf)

    /** True once read with no tracking updates: the "none received yet" line shows. */
    val showsNoUpdates: Boolean get() = incident != null && updateCount == 0

    private fun elapsedOf(alert: Alert): Elapsed? =
        BandStatus.parseIsoUtcMillis(alert.timestamp)?.let { BandStatus.elapsedSince(it, nowMillis) }
}

/** One-shot events emitted by [IncidentViewModel]. */
sealed interface IncidentEvent {
    /** Toast text: a refresh failed while the path was on screen. */
    data class ShowMessage(val text: String) : IncidentEvent
}
