package com.example.guardband.testing

import com.example.guardband.data.model.Alert
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.AlertRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * [AlertRepository] whose emissions a test drives by hand, so it can push new
 * data or a failure at a live collector mid-test.
 *
 * Backed by a replaying [MutableSharedFlow] rather than a `StateFlow`, because
 * a StateFlow drops an emission equal to the one before it - which would make
 * "the same history, but this time the read failed" invisible to the
 * collector. The replay also means a value emitted *before* the ViewModel is
 * built still reaches it.
 *
 * Unlike [InMemoryAlertRepository][com.example.guardband.data.repository.InMemoryAlertRepository]
 * this one can fail, which is what the error and retry paths need.
 */
class FakeAlertRepository(
    history: AlertHistory = AlertHistory(),
    latest: Alert? = null
) : AlertRepository {

    private val historyFlow = replayingFlow<Result<AlertHistory>>()
    private val latestFlow = replayingFlow<Result<Alert?>>()

    init {
        historyFlow.tryEmit(Result.success(history))
        latestFlow.tryEmit(Result.success(latest))
    }

    /** Every `limit` ever passed to [observeAlertHistory], in call order. */
    val requestedLimits = mutableListOf<Int>()

    /** How many times a collector subscribed to the history flow. */
    var historySubscriptions = 0
        private set

    /**
     * How many times a collector subscribed to the latest-alert flow.
     *
     * The Alert tab must leave this at zero: D2 derives its card from history,
     * so opening a second listener on `devices/{id}/latest` would be waste.
     */
    var latestSubscriptions = 0
        private set

    override fun observeLatestAlert(): Flow<Result<Alert?>> {
        latestSubscriptions++
        return latestFlow
    }

    override fun observeAlertHistory(limit: Int): Flow<Result<AlertHistory>> {
        requestedLimits += limit
        historySubscriptions++
        return historyFlow
    }

    /** Pushes a new history at any live collector. */
    fun emitHistory(history: AlertHistory) {
        historyFlow.tryEmit(Result.success(history))
    }

    /** Pushes a new latest alert at any live collector. */
    fun emitLatest(alert: Alert?) {
        latestFlow.tryEmit(Result.success(alert))
    }

    /** Makes the history read fail, now and for the next subscriber. */
    fun failHistory(error: Throwable) {
        historyFlow.tryEmit(Result.failure(error))
    }

    /** Makes the latest read fail, now and for the next subscriber. */
    fun failLatest(error: Throwable) {
        latestFlow.tryEmit(Result.failure(error))
    }

    companion object {
        private fun <T> replayingFlow() = MutableSharedFlow<T>(
            replay = 1,
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

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
