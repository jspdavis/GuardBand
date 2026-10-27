package com.example.guardband.testing

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.CheckIn
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
    latest: Alert? = null,
    latestCheckIn: CheckIn? = null
) : AlertRepository {

    private val historyFlow = replayingFlow<Result<AlertHistory>>()
    private val latestFlow = replayingFlow<Result<Alert?>>()
    private val checkInFlow = replayingFlow<Result<CheckIn?>>()

    init {
        historyFlow.tryEmit(Result.success(history))
        latestFlow.tryEmit(Result.success(latest))
        checkInFlow.tryEmit(Result.success(latestCheckIn))
    }

    /** Every `limit` ever passed to [observeAlertHistory], in call order. */
    val requestedLimits = mutableListOf<Int>()

    /** How many times a collector subscribed to the history flow. */
    var historySubscriptions = 0
        private set

    /**
     * How many collectors are subscribed to the history flow right now: the
     * stand-in for a live RTDB listener, which `awaitClose` removes when its
     * collector goes away.
     */
    val activeHistoryCollectors: Int
        get() = historyFlow.subscriptionCount.value

    /**
     * How many times a collector subscribed to the latest-alert flow.
     *
     * The alert screens' ViewModel must leave this at zero: D2 derives its card from history,
     * so opening a second listener on `devices/{id}/latest` would be waste.
     */
    var latestSubscriptions = 0
        private set

    /** How many times a collector subscribed to the check-in flow. */
    var checkInSubscriptions = 0
        private set

    /**
     * Its own flow rather than one derived from [emitHistory], so a test can
     * drive the check-in status without building a history around it. The
     * derivation itself is [CheckInSelector][com.example.guardband.data.repository.CheckInSelector]'s,
     * and is tested there.
     */
    override fun observeLatestCheckIn(): Flow<Result<CheckIn?>> {
        checkInSubscriptions++
        return checkInFlow
    }

    override fun observeLatestAlert(): Flow<Result<Alert?>> {
        latestSubscriptions++
        return latestFlow
    }

    override fun observeAlertHistory(limit: Int): Flow<Result<AlertHistory>> {
        requestedLimits += limit
        historySubscriptions++
        return historyFlow
    }

    private val incidentFlow = replayingFlow<Result<AlertHistory>>()

    /** Every panic an Incident read was opened for, in call order. */
    val requestedIncidents = mutableListOf<Long>()

    /** How many collectors are subscribed to the incident flow right now. */
    val activeIncidentCollectors: Int
        get() = incidentFlow.subscriptionCount.value

    /**
     * Its own flow, separate from [emitHistory], as the real read is its own
     * query. Empty until a test emits, so a ViewModel stays loading.
     */
    override fun observeIncident(panicSequenceId: Long, limit: Int): Flow<Result<AlertHistory>> {
        requestedIncidents += panicSequenceId
        return incidentFlow
    }

    /** Pushes what an Incident read returns at any live collector. */
    fun emitIncident(history: AlertHistory) {
        incidentFlow.tryEmit(Result.success(history))
    }

    /** Makes the Incident read fail, now and for the next subscriber. */
    fun failIncident(error: Throwable) {
        incidentFlow.tryEmit(Result.failure(error))
    }

    /** Pushes a new history at any live collector. */
    fun emitHistory(history: AlertHistory) {
        historyFlow.tryEmit(Result.success(history))
    }

    /** Pushes a new latest alert at any live collector. */
    fun emitLatest(alert: Alert?) {
        latestFlow.tryEmit(Result.success(alert))
    }

    /** Pushes a new latest check-in (or none) at any live collector. */
    fun emitCheckIn(checkIn: CheckIn?) {
        checkInFlow.tryEmit(Result.success(checkIn))
    }

    /** Makes the check-in read fail, now and for the next subscriber. */
    fun failCheckIn(error: Throwable) {
        checkInFlow.tryEmit(Result.failure(error))
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
