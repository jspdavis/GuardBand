package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import kotlinx.coroutines.flow.Flow

/**
 * Alerts written by the paired band, read live.
 *
 * The device id is fixed inside the implementation (see
 * [com.example.guardband.data.DeviceConstants]) so pairing can replace it
 * later without touching ViewModels.
 *
 * Both flows emit once the first read completes and again on every change.
 * A failed read is emitted as [Result.failure] whose message is the
 * user-facing error text, instead of being swallowed. Collect them from the
 * main thread (e.g. viewModelScope).
 */
interface AlertRepository {
    /** /devices/{id}/latest. Emits null while the device has no alerts. */
    fun observeLatestAlert(): Flow<Result<Alert?>>

    /** /devices/{id}/history, newest first (highest sequenceId first). */
    fun observeAlertHistory(): Flow<Result<List<Alert>>>
}
