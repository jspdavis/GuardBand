package com.example.guardband.ui.mocksender

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import kotlin.random.Random

/**
 * AlertSender - Temporary test harness for simulating GuardBand wearable device alerts.
 * This class stands in for ESP32 firmware until real hardware is available.
 * 
 * NOTE: Replace INGEST_ALERT_URL with the actual Cloud Function URL once Jedd deploys `ingestAlert`.
 */
const val INGEST_ALERT_URL = "REPLACE_ME_ONCE_DEPLOYED"

class AlertSender {
    private val client = OkHttpClient()
    private var sequenceId = 1

    /**
     * Sends an alert payload to the backend endpoint.
     * @param type The type of alert to send
     * @return Result<Unit> indicating success or failure
     */
    suspend fun sendAlert(type: AlertType): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = buildPayload(type)
            
            val requestBody = payload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(INGEST_ALERT_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                sequenceId++
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPayload(type: AlertType): String {
        // Hardcoded/randomized values for mock testing
        val batteryPercent = Random.nextInt(20, 101)
        val isCharging = Random.nextBoolean()
        val lat = 10.3157 + (Random.nextDouble(-0.01, 0.01)) // Cebu area with slight variation
        val lng = 123.8854 + (Random.nextDouble(-0.01, 0.01))
        val accuracy = Random.nextDouble(5.0, 15.0)

        return """
        {
            "schemaVersion": "1.0",
            "deviceId": "guardband-001",
            "type": "${type.name}",
            "timestamp": "${Instant.now()}",
            "location": {
                "lat": $lat,
                "lng": $lng,
                "accuracyMeters": $accuracy
            },
            "battery": {
                "percent": $batteryPercent,
                "isCharging": $isCharging
            },
            "sequenceId": $sequenceId
        }
        """.trimIndent()
    }
}
