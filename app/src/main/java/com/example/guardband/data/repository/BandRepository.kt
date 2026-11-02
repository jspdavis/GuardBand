package com.example.guardband.data.repository

import com.example.guardband.data.model.BandRelation
import com.example.guardband.data.model.PairedBand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The bands the signed-in account wears or watches: the one question pairing
 * answers (docs/device-pairing.md).
 *
 * Every screen that reads a band gets its id from here, through
 * [WatchedBandAlertRepository] and [BandSelection], never from a constant.
 * ViewModels never see a device id for reading (D4); the Device tab sees one
 * for display only.
 */
interface BandRepository {

    /** Every band this account wears or watches; empty when it has none. */
    fun observeBands(): Flow<List<PairedBand>>
}

/**
 * Pairing before pairing exists: every account wears the one hard-coded band.
 *
 * No listener and no Firebase, so it never fails. A Firebase implementation
 * replaces it behind the same interface once bands can be claimed, following
 * the signed-in account.
 */
class FixedBandRepository(private val bandId: String) : BandRepository {

    override fun observeBands(): Flow<List<PairedBand>> =
        flowOf(listOf(PairedBand(bandId, BandRelation.WEARER)))
}
