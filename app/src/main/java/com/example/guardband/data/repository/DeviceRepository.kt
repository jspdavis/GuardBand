package com.example.guardband.data.repository

import android.util.Log
import com.example.guardband.utils.DevicePaths
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Repository for managing device control and status via Firebase Realtime Database.
 * Uses DatabaseManager singleton to ensure connection to correct Asia-Southeast database.
 */
class DeviceRepository {
    private val database: FirebaseDatabase = DatabaseManager.database
    private val devicesRef: DatabaseReference = database.reference.child(DevicePaths.DEVICES)

    companion object {
        private const val TAG = "DeviceRepository"
    }

    init {
        Log.d(TAG, "DeviceRepository initialized with database: ${database.reference.database.app.name}")
    }

    /**
     * Send LED command to device.
     * Writes to devices/{deviceId}/command/led
     */
    fun sendLedCommand(
        deviceId: String,
        state: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        devicesRef
            .child(deviceId)
            .child(DevicePaths.COMMAND)
            .child(DevicePaths.LED)
            .setValue(state)
            .addOnSuccessListener {
                Log.d(TAG, "LED command sent: $state for device $deviceId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send LED command", e)
                onError(e.localizedMessage ?: "Failed to send LED command")
            }
    }

    /**
     * Observe LED status from device (real-time updates).
     * Listens to devices/{deviceId}/status/led
     */
    fun observeLedStatus(deviceId: String): Flow<Boolean?> = callbackFlow {
        Log.d(TAG, "Starting LED status observer for device: $deviceId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.getValue(Boolean::class.java)
                Log.d(TAG, "LED status update - exists: ${snapshot.exists()}, value: $value")
                try {
                    if (value != null) {
                        trySend(value).isSuccess
                    } else {
                        Log.d(TAG, "LED status is null or invalid for device $deviceId")
                        trySend(null).isSuccess
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending LED status", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "LED status listener cancelled: ${error.message}")
                try {
                    trySend(null).isSuccess
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending null on cancel", e)
                }
            }
        }

        val statusRef = devicesRef
            .child(deviceId)
            .child(DevicePaths.STATUS)
            .child(DevicePaths.LED)
        Log.d(TAG, "LED status listener path: ${statusRef.path}")
        statusRef.addValueEventListener(listener)

        awaitClose {
            Log.d(TAG, "Removing LED status listener for device: $deviceId")
            statusRef.removeEventListener(listener)
        }
    }

    /**
     * Observe device online status using lastSeen heartbeat.
     * Device is considered online if lastSeen updated within last 10 seconds.
     */
    fun observeDeviceOnline(deviceId: String): Flow<Boolean> = callbackFlow {
        Log.d(TAG, "=== STARTING ONLINE OBSERVER (HEARTBEAT MODE) ===")
        Log.d(TAG, "Device ID: $deviceId")
        
        var latestHeartbeat: Long = 0
        
        val heartbeatListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java)
                
                Log.d(TAG, "!!! RAW Firebase value: $timestamp, type: ${snapshot.value?.javaClass?.simpleName}")
                
                if (timestamp != null && timestamp > 0) {
                    latestHeartbeat = timestamp
                    val currentTime = System.currentTimeMillis() / 1000
                    val age = currentTime - timestamp
                    
                    Log.d(TAG, ">>> ESP32 timestamp: $timestamp")
                    Log.d(TAG, ">>> Android time: $currentTime") 
                    Log.d(TAG, ">>> Age calculation: $currentTime - $timestamp = ${age}s")
                    
                    // Consider online if heartbeat is fresh (within 10 seconds)
                    // Handle negative age (clock skew) by treating as online
                    val isOnline = (age >= -5 && age <= 10)
                    
                    Log.d(TAG, ">>> DECISION: age=${age}s, isOnline=$isOnline")
                    
                    try {
                        trySend(isOnline).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending online status", e)
                    }
                } else {
                    Log.d(TAG, ">>> No heartbeat data - offline")
                    try {
                        trySend(false).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending offline status", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "!!! Heartbeat listener CANCELLED: ${error.message}")
                try {
                    trySend(false).isSuccess
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending false on cancel", e)
                }
            }
        }
        
        val lastSeenRef = devicesRef
            .child(deviceId)
            .child(DevicePaths.STATUS)
            .child(DevicePaths.LAST_SEEN)
        
        Log.d(TAG, "Attaching heartbeat listener to: ${lastSeenRef.path}")
        lastSeenRef.addValueEventListener(heartbeatListener)
        Log.d(TAG, "Listener attached successfully")
        
        // Periodic staleness check every 3 seconds
        launch {
            while (isActive) {
                delay(3000)
                
                if (latestHeartbeat > 0) {
                    val currentTime = System.currentTimeMillis() / 1000
                    val age = currentTime - latestHeartbeat
                    val isOnline = (age >= -5 && age <= 10)
                    
                    Log.d(TAG, ">>> STALENESS CHECK: timestamp=$latestHeartbeat, now=$currentTime, age=${age}s, online=$isOnline")
                    
                    try {
                        trySend(isOnline).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in staleness check", e)
                    }
                }
            }
        }

        awaitClose {
            Log.d(TAG, "=== CLOSING ONLINE OBSERVER ===")
            lastSeenRef.removeEventListener(heartbeatListener)
            Log.d(TAG, "=== OBSERVER CLOSED ===")
        }
    }
}
