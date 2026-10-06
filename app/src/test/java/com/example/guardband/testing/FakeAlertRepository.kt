package com.example.guardband.testing

import com.example.guardband.data.model.Alert
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [AlertRepository] holding its emissions in flows, so a test can push new
 * data or a failure at a live collector mid-test.
 *
 * Unlike [InMemoryAlertRepository][com.example.guardband.data.repository.InMemoryAlertRepository]
 * this one can fail, which is what the error and retry paths need.
 */
class FakeAlertRepository(
    history: AlertHistory = AlertHistory(),
    latest: Alert? = null
) : AlertRepository {

    private val historyState = MutableStateFlow(history)
    private val latestState = MutableStateFlow(latest)

    /** Emitted instead of the history when a test wants that read to fail. */
    var historyResult: Result<AlertHistory>? = null

    /** Emitted instead of the latest alert when a test wants that read to fail. */
    var latestResult: Result<Alert?>? = null

    /** Every `limit` ever passed to [observeAlertHistory], in call order. */
    val requestedLimits = mutableListOf<Int>()

    /** How many times a collector subscribed to the history flow. */
    var historySubscriptions = 0
        private set

    override fun observeLatestAlert(): Flow<Result<Alert?>> =
        latestState.map { latestResult ?: Result.success(it) }

    override fun observeAlertHistory(limit: Int): Flow<Result<AlertHistory>> {
        requestedLimits += limit
        historySubscriptions++
        return historyState.map { historyResult ?: Result.success(it) }
    }

    /** Pushes a new history at any live collector. */
    fun emitHistory(history: AlertHistory) {
        historyResult = null
        historyState.value = history
    }

    /** Pushes a new latest alert at any live collector. */
    fun emitLatest(alert: Alert?) {
        latestResult = null
        latestState.value = alert
    }

    /**
     * Makes the next history emission fail, and nudges the flow so a live
     * collector actually sees it.
     */
    fun failHistory(error: Throwable) {
        historyResult = Result.failure(error)
        historyState.value = historyState.value.copy()
    }

    companion object {
        /** An alert with SCHEMA.md's shape; override what a test cares about. */
        fun alert(
            sequenceId: Long,
            type: String,
            timestamp: String = "2026-10-04T08:45:00Z",
            deviceId: String = "guardband-001"
        ) = Alert(
            schemaVersion = "1.0",
            deviceId = deviceId,
            type = type,
            timestamp = timestamp,
            location = Alert.Location(lat = 10.3157, lng = 123.8854, accuracyMeters = 8.5),
            battery = Alert.Battery(percent = 64, isCharging = false),
            sequenceId = sequenceId
        )
    }
}
