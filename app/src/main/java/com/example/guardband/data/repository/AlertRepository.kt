package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing alert data from Firebase Realtime Database
 * Uses Firebase SDK with ValueEventListener for real-time updates
 */
class AlertRepository {
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val devicesRef: DatabaseReference = database.reference.child("devices")

    companion object {
        private const val LATEST_PATH = "latest"
        private const val HISTORY_PATH = "history"
    }

    /**
     * Fetch the latest alert for a device (single read)
     * @param deviceId The device identifier
     * @return Alert object or null if not found
     */
    suspend fun fetchLatestAlert(deviceId: String): Alert? {
        return try {
            val snapshot = devicesRef
                .child(deviceId)
                .child(LATEST_PATH)
                .get()
                .await()

            snapshot.getValue(Alert::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Observe the latest alert for a device (real-time updates)
     * @param deviceId The device identifier
     * @return Flow emitting Alert updates
     */
    fun observeLatestAlert(deviceId: String): Flow<Alert?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alert = snapshot.getValue(Alert::class.java)
                trySend(alert)
            }

            override fun onCancelled(error: DatabaseError) {
                // Send null on error
                trySend(null)
            }
        }

        val latestRef = devicesRef.child(deviceId).child(LATEST_PATH)
        latestRef.addValueEventListener(listener)

        awaitClose {
            latestRef.removeEventListener(listener)
        }
    }

    /**
     * Fetch alert history for a device (single read)
     * @param deviceId The device identifier
     * @return List of alerts sorted by sequenceId descending (most recent first)
     */
    suspend fun fetchAlertHistory(deviceId: String): List<Alert> {
        return try {
            val snapshot = devicesRef
                .child(deviceId)
                .child(HISTORY_PATH)
                .get()
                .await()

            val alerts = mutableListOf<Alert>()
            snapshot.children.forEach { child ->
                child.getValue(Alert::class.java)?.let { alert ->
                    alerts.add(alert)
                }
            }

            // Sort by sequenceId descending (most recent first)
            alerts.sortedByDescending { it.sequenceId }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Observe alert history for a device (real-time updates)
     * @param deviceId The device identifier
     * @return Flow emitting List<Alert> updates
     */
    fun observeAlertHistory(deviceId: String): Flow<List<Alert>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<Alert>()
                snapshot.children.forEach { child ->
                    child.getValue(Alert::class.java)?.let { alert ->
                        alerts.add(alert)
                    }
                }
                // Sort by sequenceId descending (most recent first)
                val sortedAlerts = alerts.sortedByDescending { it.sequenceId }
                trySend(sortedAlerts)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }

        val historyRef = devicesRef.child(deviceId).child(HISTORY_PATH)
        historyRef.addValueEventListener(listener)

        awaitClose {
            historyRef.removeEventListener(listener)
        }
    }
}
