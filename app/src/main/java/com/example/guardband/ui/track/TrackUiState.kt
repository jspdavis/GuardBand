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
    val errorMessage: String? = null,

    /**
     * The Check-in pill: the wearer's latest check-in, from the history
     * window rather than `latest`. Independent of everything above - a
     * failed check-in read never touches the map, and a failed `latest`
     * read never touches the pill.
     */
    val checkIn: CheckInStatus = CheckInStatus.Loading,

    /**
     * The selected base map (D7), kept here so it survives a theme change: the
     * Fragment is recreated on a theme switch and re-applies it. The ViewModel
     * starts it from [MapLayerPrefs], so it also survives the app closing;
     * [MapLayer.AUTO] is a fresh install's. Street, Satellite, Outdoor, and
     * Topo have no dark variant, so they draw the same in either mode.
     */
    val selectedLayer: MapLayer = MapLayer.AUTO,

    /**
     * What the band's marker shows: a photo link and the initials it falls
     * back on. Set once, from the session ([TrackViewModel]'s `markerFace`),
     * and carried unchanged through every position update, so the marker is
     * drawn again only if the face itself changes.
     */
    val markerFace: BandMarkerFace = BandMarkerFace(photoUrl = null, initials = ""),

    /**
     * Recent alerts as small pins where they happened, newest first, chosen
     * by [AlertPinSelector] from the same list as the recent-alerts sheet.
     * Drawn under the band's face marker.
     */
    val alertPins: List<AlertPin> = emptyList(),

    /**
     * The small label open over a tapped pin ("Panic, 5 min ago"), or null
     * when none is. Always one of [alertPins]: a pin that drops out closes
     * its label.
     */
    val pinLabel: AlertPinLabel? = null,

    /**
     * The panic the band's bubble offers as "View incident", or null when it
     * offers none. Set while the band's latest **non-tracking** alert (D2's
     * latest, the one Alert history's card shows) is a panic: it stays through
     * the tracking updates that follow it, and goes when a check-in or a
     * low-battery alert arrives. The way to a panic whose pin the face hides.
     */
    val bandPanic: Alert? = null
) {
    /** Whether there is a position to put a marker on (D3, D4). */
    val hasFix: Boolean get() = location != null

    /**
     * The round re-centre button (D4) needs a last known position to go back
     * to. Named for its view id, `track_navigate_button`.
     */
    val navigateEnabled: Boolean get() = location != null
}

/**
 * The label over a tapped alert pin: its type and how long ago it was. The
 * Fragment words it ("Panic, 5 min ago") with the same strings and plurals as
 * the alert rows, because a ViewModel cannot reach a `plurals`.
 *
 * @param elapsed recomputed on [TrackViewModel]'s ticker, so the label ages
 *   while it is open; null when the timestamp could not be read.
 */
data class AlertPinLabel(
    val pin: AlertPin,
    val elapsed: Elapsed?
)
