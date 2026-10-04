package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [AlertRepository] backed by the seeded alerts in [InMemoryStore].
 *
 * Stands in until the Firebase pass. The first emission waits
 * [InMemoryStore.SIMULATED_DELAY_MS]; after that the flows follow the store,
 * so any alert added there shows up live. Neither flow can fail.
 *
 * @param deviceId Only alerts from this device are returned.
 */
class InMemoryAlertRepository(
    private val deviceId: String
) : AlertRepository {

    override fun observeLatestAlert(): Flow<Result<Alert?>> =
        observeDeviceAlerts().map { alerts ->
            Result.success(alerts.maxByOrNull { it.sequenceId })
        }

    override fun observeAlertHistory(): Flow<Result<List<Alert>>> =
        observeDeviceAlerts().map { alerts ->
            Result.success(alerts.sortedByDescending { it.sequenceId })
        }

    private fun observeDeviceAlerts(): Flow<List<Alert>> = flow {
        delay(InMemoryStore.SIMULATED_DELAY_MS)
        emitAll(InMemoryStore.alerts.map { all -> all.filter { it.deviceId == deviceId } })
    }
}
