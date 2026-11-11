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

    /**
     * Records [deviceId] as the band this account wears, **replacing** any it
     * wore before: an account wears one band (docs/device-pairing.md §4).
     *
     * Pairing stage 1 (2026-11-11): the id is trusted as typed. Before
     * `devices/` reads are closed a one-time code from the band has to back
     * it (stage 2). [deviceId] must already be checked by [BandIdInput].
     *
     * Fails with [BandError.NotSignedIn], [BandError.PermissionDenied] or
     * [BandError.Network] (offline included: the write times out rather than
     * queueing).
     */
    suspend fun claimBand(deviceId: String): Result<Unit>
}

/** Why a band write failed. Carries no message, like [AlertError]. */
sealed class BandError : Exception() {
    object NotSignedIn : BandError()
    object PermissionDenied : BandError()
    object Network : BandError()

    /** Suppressed for the same reason as [ContactError.fillInStackTrace]. */
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Pairing before pairing existed: every account wears the one hard-coded band.
 *
 * No listener and no Firebase, so it never fails. **Not wired since
 * 2026-11-10** ([FirebaseBandRepository] is); kept as the simplest stand-in.
 * It cannot claim a band.
 */
class FixedBandRepository(private val bandId: String) : BandRepository {

    override fun observeBands(): Flow<List<PairedBand>> =
        flowOf(listOf(PairedBand(bandId, BandRelation.WEARER)))

    override suspend fun claimBand(deviceId: String): Result<Unit> =
        Result.failure(BandError.PermissionDenied)
}
