package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.ui.alert.AlertFormatting

/**
 * What the band's reports add up to: whether it has reported at all, its
 * last known position and battery, and when it last spoke. **The one place
 * those rules live**, shared by the Track tab ([TrackViewModel], fed from
 * `devices/{id}/latest`) and the Device tab
 * ([DeviceViewModel][com.example.guardband.ui.device.DeviceViewModel], fed the
 * newest entry of the history window). Pure and immutable, so it runs in a
 * plain JVM test.
 *
 * Feed it each newest report with [after]; ask it how fresh that is with
 * [isReporting] and [elapsed], at whatever time the caller's ticker says.
 *
 * Two rules, both Track's from the start:
 *  - **The last known position is retained** across reports that carry none
 *    (Track's D3). A tracking ping with no fix means the band is talking but
 *    cannot see the sky; blanking the position then would throw away the
 *    most useful thing on screen. Battery follows the same rule.
 *  - **A null report clears everything.** It is the repository's explicit
 *    "the band has never reported", so there is nothing to be the last known
 *    anything.
 *
 * Holds the wearer's coordinates: never log one.
 *
 * @property hasReported the band has been seen reporting at all. False is
 *   different from "reported without a fix", and reads differently.
 * @property location last **known** position; null until the band has sent a fix.
 * @property battery last known battery reading, retained like [location].
 * @property reportedAtMillis when the newest report was made, from its
 *   timestamp; null when it has never reported, or when that timestamp could
 *   not be read.
 * @property locationFromTracking whether [location] came from a
 *   TRACKING_UPDATE rather than an alert. The Device tab names a position
 *   only when it was an alert's, because tracking positions are never sent
 *   to the geocoder.
 */
data class BandReport(
    val hasReported: Boolean = false,
    val location: Alert.Location? = null,
    val battery: Alert.Battery? = null,
    val reportedAtMillis: Long? = null,
    val locationFromTracking: Boolean = false
) {

    /** This record after [report], the band's newest; null means it has never reported. */
    fun after(report: Alert?): BandReport {
        if (report == null) return NONE
        val fix = report.location
        return BandReport(
            hasReported = true,
            location = fix ?: location,
            battery = report.battery ?: battery,
            reportedAtMillis = BandStatus.parseIsoUtcMillis(report.timestamp),
            locationFromTracking = if (fix != null) {
                report.alertType() == AlertType.TRACKING_UPDATE
            } else {
                locationFromTracking
            }
        )
    }

    /**
     * Whether the band reported within [BandStatus.ONLINE_THRESHOLD_MS] of
     * [nowMillis]: "Reporting", never "Online". False when it has never
     * reported, or its timestamp could not be read: "we cannot say how fresh
     * this is" must not read as "it is fresh".
     */
    fun isReporting(nowMillis: Long): Boolean =
        reportedAtMillis != null && BandStatus.isOnline(reportedAtMillis, nowMillis)

    /** How long ago the band last reported, for "Last seen"; null as for [isReporting]. */
    fun elapsed(nowMillis: Long): Elapsed? =
        reportedAtMillis?.let { BandStatus.elapsedSince(it, nowMillis) }

    /**
     * FR-11's low level, [AlertFormatting.LOW_BATTERY_PERCENT] or below: the
     * battery shows orange. Charging does not change it here; the symbol
     * ([AlertFormatting.batteryColor]) decides what charging looks like.
     */
    val isBatteryLow: Boolean
        get() = battery?.let { it.percent <= AlertFormatting.LOW_BATTERY_PERCENT } ?: false

    companion object {
        /** Nothing reported yet. */
        val NONE = BandReport()
    }
}
