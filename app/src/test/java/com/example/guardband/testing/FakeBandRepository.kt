package com.example.guardband.testing

import com.example.guardband.data.model.BandRelation
import com.example.guardband.data.model.PairedBand
import com.example.guardband.data.repository.BandRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [BandRepository] whose bands a test sets by hand, mid-test if it likes:
 * pairing, unpairing, another account signing in.
 *
 * A [MutableStateFlow] is fine here, unlike [FakeAlertRepository]: the same
 * list twice means nothing changed, which is exactly what the readers
 * de-duplicate anyway.
 */
class FakeBandRepository(bands: List<PairedBand> = listOf(wearer(BAND_ID))) : BandRepository {

    val bands = MutableStateFlow(bands)

    override fun observeBands(): Flow<List<PairedBand>> = bands

    companion object {
        const val BAND_ID = "guardband-001"

        fun wearer(deviceId: String) = PairedBand(deviceId, BandRelation.WEARER)

        fun watcher(deviceId: String) = PairedBand(deviceId, BandRelation.WATCHER)
    }
}
