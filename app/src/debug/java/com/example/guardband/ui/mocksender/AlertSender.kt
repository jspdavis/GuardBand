package com.example.guardband.ui.mocksender

import com.example.guardband.data.DeviceConstants
import com.example.guardband.data.FirebaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * AlertSender - Temporary test harness for simulating GuardBand wearable device alerts.
 * This class stands in for ESP32 firmware until real hardware is available.
 *
 * It writes straight to the Realtime Database REST API, exactly as the band
 * will: one PUT to `/devices/{id}/history/{sequenceId}.json`, then one to
 * `/devices/{id}/latest.json`. There is no Cloud Function in the path - the
 * Spark plan cannot host one, and the `devices` Security Rules already do the
 * payload validation that `ingestAlert` was going to do.
 *
 * Two deliberate choices, both because this is throwaway debug-only code
 * rather than part of the app's architecture:
 *
 *  - It reads the database URL from [FirebaseProvider] instead of hardcoding
 *    one, so the harness follows `google-services.json` to whichever project
 *    the build points at. That is a Firebase import outside a repository,
 *    which the app's layering forbids; the harness is outside that layering,
 *    and the alternative was a hardcoded URL, which is forbidden everywhere.
 *  - [AlertType] stays a private second copy of the enum, as documented, but
 *    the device id comes from [DeviceConstants] so the two cannot drift.
 */
class AlertSender {

    private val client = OkHttpClient()

    /**
     * The id the next alert will use, or null until the first send seeds it
     * from whatever is already in the database. Seeding from the server, rather
     * than from 1, is what stops a relaunched harness from overwriting the
     * history rows it wrote last time.
     */
    private var nextSequenceId: Int? = null

    private val databaseUrl: String by lazy {
        FirebaseProvider.database.reference.toString().trimEnd('/')
    }

    /**
     * Writes one alert to the database.
     *
     * History is written before `latest` on purpose. The Alert tab derives
     * everything it shows from history, so a half-finished send that got as
     * far as history is still visible there and only leaves the Track-facing
     * `latest` node stale. The reverse - `latest` written, history missing -
     * would show the alert nowhere in the Alert tab and leave a permanent hole
     * in the history. For the same reason the sequence id is consumed as soon
     * as its history row lands, so a retry cannot overwrite a good row.
     *
     * @param type The type of alert to send
     * @return the sequenceId that was written, or the failure that stopped it
     */
    suspend fun sendAlert(type: AlertType): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sequenceId = nextSequenceId ?: (readLatestSequenceId() + 1)
            val payload = buildPayload(type, sequenceId)

            putJson("devices/$DEVICE_ID/history/$sequenceId.json", payload)
            nextSequenceId = sequenceId + 1
            putJson("devices/$DEVICE_ID/latest.json", payload)

            Result.success(sequenceId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The highest sequence id already in the database, or 0 when the device has
     * never reported. A missing path returns the body `null`, not an error, so
     * only a genuine read failure throws - and that is worth failing the send
     * for, because a PUT would be about to fail the same way.
     *
     * This reads `latest`, which every send overwrites, so it is the real high
     * water mark. It can only lag if a previous send wrote history and then
     * failed on `latest`; within one run the cached [nextSequenceId] covers
     * that, across runs it would re-use one id.
     */
    private fun readLatestSequenceId(): Int {
        val request = Request.Builder()
            .url("$databaseUrl/devices/$DEVICE_ID/latest/sequenceId.json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} reading the last sequenceId")
            }
            return response.body?.string()?.trim()?.toIntOrNull() ?: 0
        }
    }

    /** PUTs [json] to [path] under the database root, throwing on any non-2xx. */
    private fun putJson(path: String, json: String) {
        val request = Request.Builder()
            .url("$databaseUrl/$path")
            .put(json.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // The path, not the full URL: the status line has to stay short
                // enough to read on a phone.
                throw IOException("HTTP ${response.code} on PUT /$path: ${response.message}")
            }
        }
    }

    private fun buildPayload(type: AlertType, sequenceId: Int): String {
        // Hardcoded/randomized values for mock testing
        val batteryPercent = Random.nextInt(20, 101)
        val isCharging = Random.nextBoolean()
        val lat = 10.3157 + (Random.nextDouble(-0.01, 0.01)) // Cebu area with slight variation
        val lng = 123.8854 + (Random.nextDouble(-0.01, 0.01))
        val accuracy = Random.nextDouble(5.0, 15.0)

        return """
        {
            "schemaVersion": "1.0",
            "deviceId": "$DEVICE_ID",
            "type": "${type.name}",
            "timestamp": "${isoUtcNow()}",
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

    /**
     * Current time as ISO 8601 UTC, e.g. "2026-10-04T08:15:30.123Z".
     * Uses SimpleDateFormat because java.time.Instant needs API 26 (minSdk is 24).
     */
    private fun isoUtcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private companion object {
        val JSON = "application/json".toMediaType()
        const val DEVICE_ID = DeviceConstants.DEFAULT_DEVICE_ID
    }
}
