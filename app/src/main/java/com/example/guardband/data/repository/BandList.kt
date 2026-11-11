package com.example.guardband.data.repository

import com.example.guardband.data.DeviceConstants
import com.example.guardband.data.model.BandRelation
import com.example.guardband.data.model.PairedBand

/**
 * The account's band list, from what [FirebaseBandRepository] read. **The one
 * place that decides it**, pure so it is a plain JVM test (`BandListTest`).
 *
 * - **Worn:** the bands the account has claimed (`users/{uid}/bands`). One
 *   that has claimed none wears the shared demo band,
 *   [DeviceConstants.DEFAULT_DEVICE_ID], so the demo and the mock sender's
 *   default keep working until every band is claimed.
 * - **Watched:** the bands the connected wearers have claimed. A wearer who
 *   has claimed none adds **nothing** (decided 2026-11-11): the switcher says
 *   so instead of showing a band that is not theirs.
 *
 * A band is listed once; worn wins over watched.
 *
 * Never an account uid as a band id: nothing writes `devices/<account uid>`,
 * and listing it emptied every screen from 2026-11-10 to 2026-11-11.
 */
internal object BandList {

    /**
     * @param claimed the keys under `users/{uid}/bands`; empty when there are
     *   none or the read failed.
     * @param watched the keys under each connected wearer's
     *   `users/{wearer}/bands`, flattened; empty entries are simply absent.
     */
    fun of(claimed: List<String>, watched: List<String>): List<PairedBand> {
        val worn = claimed.distinct().ifEmpty { listOf(DeviceConstants.DEFAULT_DEVICE_ID) }
        val wornBands = worn.map { PairedBand(it, BandRelation.WEARER) }
        val watchedBands = watched.distinct()
            .filterNot { it in worn }
            .map { PairedBand(it, BandRelation.WATCHER) }
        return wornBands + watchedBands
    }
}
