package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One recent alert drawn as a small pin on the Track map, where it happened.
 *
 * [type] is always one of [AlertPinSelector.PINNED_TYPES], and [lat] / [lng]
 * are the alert's own coordinates, already checked usable, so the map never
 * has to look inside [alert] to place the pin. [alert] is kept whole for the
 * tap hook ([TrackViewModel.onAlertPinTapped]) and whatever opens from a pin
 * later.
 */
data class AlertPin(
    val alert: Alert,
    val type: AlertType,
    val lat: Double,
    val lng: Double
)

/**
 * Which recent alerts get a pin on the Track map. Pure, so every rule below is
 * a JVM test.
 *
 *  - **Only panics, check-ins and low-battery alerts.** A TRACKING_UPDATE is
 *    the band's background chatter (FR-09, one a minute for half an hour) and
 *    would bury the incidents, as it would in the lists (D1). An unknown type
 *    is shown in the lists as "Unknown alert", but it has no colour or symbol
 *    of its own to pin it with, so it is not pinned.
 *  - **Only usable coordinates**: a location present, both numbers finite and
 *    in range, and not exactly (0, 0), which is what a GPS module reports
 *    before its first fix rather than a place anyone stood.
 *  - **Newest first, by `sequenceId`**, not by timestamp: the band's own
 *    counter is the order every other screen uses (D2, the check-in pill,
 *    the day headings), and a band whose clock jumps must not reorder its
 *    incidents. The input order does not matter either; it is sorted here.
 *  - **One pin per spot.** An alert within [MERGE_RADIUS_METERS] of a pin
 *    already chosen is dropped, so the newest alert at a spot keeps it and
 *    older ones there do not stack under it unreadably.
 *  - **The face wins.** An alert within the same radius of the band's own
 *    marker is dropped too: the face is drawn on top and takes the tap, so a
 *    pin there could only peek out from under it.
 *  - **At most [MAX_PINS]**, counted after the drops, so ten readable pins
 *    are shown whenever ten exist.
 *
 * Nothing here logs: every input is a location.
 */
object AlertPinSelector {

    /** How many pins the map shows at most. */
    const val MAX_PINS = 10

    /**
     * How close two alerts must be to share a pin, and how close one must be
     * to the band's marker to be hidden under it: **10 m**.
     *
     * A few metres is what separates two presses made standing still: the
     * NEO-6M's horizontal error is about 2.5 m in open sky and routinely
     * 5-10 m between buildings, and a phone standing in for it (the mock
     * sender) wanders as much. 10 m also matches the place names' rounding
     * ([com.example.guardband.ui.alert.AlertPlaces], about 11 m), so alerts
     * that would share a pin already share a place name. Further apart, they
     * are separate pins, which zooming in pulls apart (the map goes to
     * zoom 22, under 4 cm a pixel).
     */
    const val MERGE_RADIUS_METERS = 10.0

    /** The types that get a pin, each with its own colour and symbol. */
    val PINNED_TYPES: Set<AlertType> =
        setOf(AlertType.PANIC, AlertType.CHECKIN, AlertType.LOW_BATTERY)

    /**
     * @param alerts the alerts to choose from, in any order: the same list
     *   the recent-alerts sheet and Alert history are cut from.
     * @param bandLocation where the band's face marker is, or null when there
     *   is none; pins under it are dropped.
     * @return at most [MAX_PINS] pins, newest first.
     */
    fun select(alerts: List<Alert>, bandLocation: Alert.Location? = null): List<AlertPin> {
        val chosen = mutableListOf<AlertPin>()
        val band = bandLocation?.takeIf { isUsable(it) }

        for (alert in alerts.distinctBy { it.sequenceId }.sortedByDescending { it.sequenceId }) {
            if (chosen.size == MAX_PINS) break
            val type = alert.alertType()?.takeIf { it in PINNED_TYPES } ?: continue
            val location = alert.location?.takeIf { isUsable(it) } ?: continue

            if (band != null && isNear(location.lat, location.lng, band.lat, band.lng)) continue
            if (chosen.any { isNear(location.lat, location.lng, it.lat, it.lng) }) continue

            chosen += AlertPin(alert, type, location.lat, location.lng)
        }
        return chosen
    }

    /** Whether [location] is somewhere a pin can honestly be put. */
    fun isUsable(location: Alert.Location): Boolean {
        val (lat, lng) = location.lat to location.lng
        if (!lat.isFinite() || !lng.isFinite()) return false
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
        return !(lat == 0.0 && lng == 0.0)
    }

    private fun isNear(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean =
        distanceMeters(lat1, lng1, lat2, lng2) <= MERGE_RADIUS_METERS

    /**
     * Great-circle distance by the haversine formula. Plain maths rather than
     * `android.location.Location.distanceBetween`, so this stays a JVM test;
     * at 10 m the sphere-versus-ellipsoid difference is millimetres.
     */
    internal fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /** The mean Earth radius. */
    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
