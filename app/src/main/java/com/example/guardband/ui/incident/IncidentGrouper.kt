package com.example.guardband.ui.incident

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.ui.track.AlertPinSelector
import com.example.guardband.ui.track.BandStatus

/**
 * Which alerts open an Incident view: panics, and nothing else. The one rule
 * the tappable rows, the pins, the band's bubble and
 * [HomeViewModel.onOpenIncident][com.example.guardband.ui.home.HomeViewModel.onOpenIncident]
 * all ask, so they cannot disagree about what is tappable.
 */
object IncidentEntry {
    fun opensIncident(alert: Alert): Boolean = alert.alertType() == AlertType.PANIC
}

/**
 * One panic and the FR-09 tracking updates that followed it.
 *
 * @param trackingUpdates oldest first, by `sequenceId`; every one of them,
 *   with a position or not.
 */
data class Incident(
    val panic: Alert,
    val trackingUpdates: List<Alert>
) {
    /** The tracking updates that carry a usable position, oldest first. */
    val trackedLocations: List<Alert.Location>
        get() = trackingUpdates.mapNotNull { update ->
            update.location?.takeIf { AlertPinSelector.isUsable(it) }
        }

    /** The panic's own position, when it has a usable one. */
    val panicLocation: Alert.Location?
        get() = panic.location?.takeIf { AlertPinSelector.isUsable(it) }

    /**
     * The path to draw: the panic's point, then every tracked position, in
     * `sequenceId` order. Empty when there is nothing to place.
     */
    val path: List<Alert.Location>
        get() = listOfNotNull(panicLocation) + trackedLocations

    /** The newest tracking update, or null when none has arrived. */
    val latestUpdate: Alert? get() = trackingUpdates.lastOrNull()

    /**
     * The latest battery reading the incident holds: the newest tracking
     * update that carries one, else the panic's own.
     */
    val latestBattery: Alert.Battery?
        get() = trackingUpdates.lastOrNull { it.battery != null }?.battery ?: panic.battery
}

/**
 * Groups the tracking updates that followed a panic into its [Incident]. Pure,
 * so every rule is a JVM test.
 *
 * **There is nothing in the payload to link them by.** SCHEMA.md v1.0 has no
 * incident id or reference: a TRACKING_UPDATE carries the same seven fields as
 * every alert. So they are grouped by order and time, as FR-09 describes the
 * band sending them ("every 60 s for 30 min after an alert"):
 *
 *  - **After the panic and before the next panic, by `sequenceId`.** The
 *    band's own counter is the order every screen uses; a band whose clock
 *    jumps must not move an update into another incident. A check-in or a
 *    low-battery alert in between does not end the incident.
 *  - **Within [WINDOW_MS] (30 min) of the panic, by timestamp.** An update
 *    stamped later than that is left out; one stamped *earlier* than the panic
 *    (the band's clock went back) is kept, because the counter says it came
 *    after. When either timestamp cannot be read, only the order applies.
 *  - **Nothing before the panic belongs to it**: a tracking update with no
 *    earlier panic is part of no incident.
 *
 * The input may be in any order and may hold anything else; it is sorted and
 * filtered here. Nothing here logs: every input is a location.
 */
object IncidentGrouper {

    /** FR-09: tracking runs for 30 minutes after the alert. */
    const val WINDOW_MS = 30 * 60_000L

    /**
     * The incident started by the panic numbered [panicSequenceId], or null
     * when [alerts] holds no panic with that number.
     */
    fun incidentFor(panicSequenceId: Long, alerts: List<Alert>): Incident? {
        val ordered = alerts.distinctBy { it.sequenceId }.sortedBy { it.sequenceId }
        val panic = ordered.firstOrNull {
            it.sequenceId == panicSequenceId && it.alertType() == AlertType.PANIC
        } ?: return null
        val panicMillis = BandStatus.parseIsoUtcMillis(panic.timestamp)

        val updates = mutableListOf<Alert>()
        for (alert in ordered) {
            if (alert.sequenceId <= panicSequenceId) continue
            when (alert.alertType()) {
                AlertType.PANIC -> break
                AlertType.TRACKING_UPDATE -> if (withinWindow(panicMillis, alert)) updates += alert
                else -> Unit
            }
        }
        return Incident(panic, updates)
    }

    private fun withinWindow(panicMillis: Long?, update: Alert): Boolean {
        val updateMillis = BandStatus.parseIsoUtcMillis(update.timestamp)
        if (panicMillis == null || updateMillis == null) return true
        return updateMillis - panicMillis <= WINDOW_MS
    }
}
