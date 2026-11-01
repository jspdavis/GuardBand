package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertStatus
import com.example.guardband.data.model.CheckIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [AlertRepository] backed by the seeded alerts in [InMemoryStore].
 *
 * Not wired into [RepositoryProvider][com.example.guardband.data.RepositoryProvider]
 * any more - [FirebaseAlertRepository] is - but kept as the test double that
 * stays interchangeable with it, the same way [InMemoryContactRepository] is
 * for contacts. The first emission waits [InMemoryStore.SIMULATED_DELAY_MS];
 * after that the flows follow the store, so any alert added there shows up
 * live. Neither flow can fail, so a test that needs a failure uses
 * `FakeAlertRepository` instead.
 *
 * @param deviceId Only alerts from this device are returned.
 */
class InMemoryAlertRepository(
    private val deviceId: String
) : AlertRepository {

    private val statusesFlow = MutableStateFlow<Map<Long, AlertStatus>>(emptyMap())

    override fun observeLatestAlert(): Flow<Result<Alert?>> =
        observeDeviceAlerts().map { alerts ->
            Result.success(alerts.maxByOrNull { it.sequenceId })
        }

    override fun observeLatestCheckIn(): Flow<Result<CheckIn?>> =
        observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW)
            .map { result -> result.map(CheckInSelector::latestFrom) }

    override fun observeAlertHistory(limit: Int): Flow<Result<AlertHistory>> =
        observeDeviceAlerts().map { alerts ->
            // takeLast then sort, so the window is the newest `limit` entries
            // rather than the first ones found - the same thing
            // `limitToLast` does on the server.
            val window = alerts.sortedBy { it.sequenceId }.takeLast(limit)
            Result.success(
                AlertHistory(
                    alerts = window.sortedByDescending { it.sequenceId },
                    rawCount = window.size,
                    // Nothing in the store can be malformed: it holds typed
                    // Alerts, not wire maps.
                    malformedCount = 0
                )
            )
        }

    override fun observeIncident(panicSequenceId: Long, limit: Int): Flow<Result<AlertHistory>> =
        observeDeviceAlerts().map { alerts ->
            // From the panic on, the first `limit`: what `startAt` +
            // `limitToFirst` return on the server.
            val window = alerts
                .filter { it.sequenceId >= panicSequenceId }
                .sortedBy { it.sequenceId }
                .take(limit)
            Result.success(
                AlertHistory(
                    alerts = window.sortedByDescending { it.sequenceId },
                    rawCount = window.size,
                    malformedCount = 0
                )
            )
        }

    override fun observeStatuses(): Flow<Result<Map<Long, AlertStatus>>> =
        statusesFlow.asStateFlow().map { Result.success(it) }

    override suspend fun acknowledge(sequenceId: Long): Result<Unit> {
        statusesFlow.value = statusesFlow.value + (sequenceId to AlertStatus.ACKNOWLEDGED)
        return Result.success(Unit)
    }

    override suspend fun resolve(sequenceId: Long): Result<Unit> {
        statusesFlow.value = statusesFlow.value + (sequenceId to AlertStatus.RESOLVED)
        return Result.success(Unit)
    }

    private fun observeDeviceAlerts(): Flow<List<Alert>> = flow {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        emitAll(InMemoryStore.alerts.map { all -> all.filter { it.deviceId == deviceId } })
    }
}
