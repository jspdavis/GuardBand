package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertStatus
import com.example.guardband.data.model.CheckIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [AlertRepository] for whichever band the account is watching right now.
 *
 * It follows [BandRepository] through [BandSelection.watchedBandOf] and hands
 * every read to the repository [forBand] builds for that band. When the band
 * changes (pairing, another account signing in) `flatMapLatest` cancels the
 * old band's flow, whose `awaitClose` removes its listener, and opens the new
 * one. The same band twice is not re-read. An account with no band gets
 * [AlertError.NoBand] on every flow instead of a listener.
 *
 * This is what lets [RepositoryProvider][com.example.guardband.data.RepositoryProvider]
 * keep one alert repository for the whole process: it never captures a band
 * id, so it can never hold on to the previous account's band. ViewModels see
 * the same [AlertRepository] as before and never a device id (D4).
 *
 * [forBand] is also what Track's "All" will use later to read several bands
 * at once.
 *
 * @param forBand builds the repository that reads one band, e.g.
 *   [FirebaseAlertRepository] at `devices/{id}`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchedBandAlertRepository(
    private val bands: BandRepository,
    private val forBand: (String) -> AlertRepository
) : AlertRepository {

    private fun watchedBand(): Flow<String?> =
        bands.observeBands()
            .map(BandSelection::watchedBandOf)
            .distinctUntilChanged()

    private fun <T> followBand(read: AlertRepository.() -> Flow<Result<T>>): Flow<Result<T>> =
        watchedBand().flatMapLatest { id ->
            if (id == null) flowOf(Result.failure<T>(AlertError.NoBand)) else forBand(id).read()
        }

    override fun observeLatestAlert(): Flow<Result<Alert?>> =
        followBand { observeLatestAlert() }

    override fun observeAlertHistory(limit: Int): Flow<Result<AlertHistory>> =
        followBand { observeAlertHistory(limit) }

    override fun observeLatestCheckIn(): Flow<Result<CheckIn?>> =
        followBand { observeLatestCheckIn() }

    override fun observeIncident(panicSequenceId: Long, limit: Int): Flow<Result<AlertHistory>> =
        followBand { observeIncident(panicSequenceId, limit) }

    override fun observeStatuses(): Flow<Result<Map<Long, AlertStatus>>> =
        followBand { observeStatuses() }

    override suspend fun acknowledge(sequenceId: Long): Result<Unit> =
        watchedBand().first()?.let { forBand(it).acknowledge(sequenceId) }
            ?: Result.failure(AlertError.NoBand)

    override suspend fun resolve(sequenceId: Long): Result<Unit> =
        watchedBand().first()?.let { forBand(it).resolve(sequenceId) }
            ?: Result.failure(AlertError.NoBand)

    override suspend fun clearAlertHistory(): Result<Unit> =
        watchedBand().first()?.let { forBand(it).clearAlertHistory() }
            ?: Result.failure(AlertError.NoBand)
}
