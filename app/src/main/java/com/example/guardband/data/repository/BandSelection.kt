package com.example.guardband.data.repository

import com.example.guardband.data.model.BandRelation
import com.example.guardband.data.model.PairedBand

/**
 * Which band the alert screens show. **The one place that picks it.**
 *
 * Until Track has its selector (My band, a friend's band, All), the rule is
 * fixed: the account's own band if it wears one, else the first band it
 * watches, else none. The selector replaces this rule with the user's choice;
 * "All" reads several bands at once instead of picking one.
 *
 * Pure, so it is a plain JVM test.
 */
object BandSelection {

    /** The band to show, or null when the account has none. */
    fun watchedBandOf(bands: List<PairedBand>): String? =
        (bands.firstOrNull { it.relation == BandRelation.WEARER } ?: bands.firstOrNull())
            ?.deviceId

    /**
     * The band to show when the user has made an explicit choice in the
     * Track-tab switcher. Uses [explicit] when it identifies a band in
     * [bands]; falls back to the default rule otherwise. Null means no
     * bands at all.
     */
    fun watchedBandOf(bands: List<PairedBand>, explicit: String?): String? {
        if (explicit != null && bands.any { it.deviceId == explicit }) return explicit
        return watchedBandOf(bands)
    }

    /** The account's own band, or null when it wears none. The Device tab's band. */
    fun ownBandOf(bands: List<PairedBand>): String? =
        bands.firstOrNull { it.relation == BandRelation.WEARER }?.deviceId
}
