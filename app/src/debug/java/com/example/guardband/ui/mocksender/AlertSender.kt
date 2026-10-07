package com.example.guardband.ui.mocksender

import com.example.guardband.data.DeviceConstants
import com.example.guardband.data.FirebaseProvider
import com.example.guardband.data.model.AlertType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
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
 * It reads the database URL from [FirebaseProvider] instead of hardcoding one,
 * so the harness follows `google-services.json` to whichever project the build
 * points at. That is a Firebase import outside a repository, which the layering
 * of the app forbids; the harness is throwaway debug-only code that sits outside
 * that layering, and the alternative was a hardcoded URL, which is forbidden
 * everywhere. The device id and [AlertType] both come from the production
 * `data/` classes, so the harness cannot drift from what the app reads.
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

    /** How many walk steps have been sent, which is what moves the marker. */
    private var walkStep = 0

    private val databaseUrl: String by lazy {
        FirebaseProvider.database.reference.toString().trimEnd('/')
    }

    /**
     * Writes one alert of [type] at a randomised position near Cebu City.
     *
     * @return the sequenceId that was written, or the failure that stopped it
     */
    suspend fun sendAlert(type: AlertType): Result<Int> {
        val lat = CEBU_LAT + Random.nextDouble(-0.01, 0.01)
        val lng = CEBU_LNG + Random.nextDouble(-0.01, 0.01)
        return send(type, lat, lng, Random.nextInt(20, 101))
    }

    /**
     * Writes one TRACKING_UPDATE at the next position along the walk loop, so
     * repeated calls move the Track marker. The battery drains as it goes, so a
     * demo shows the status panel changing rather than a frozen number.
     */
    suspend fun sendWalkStep(): Result<Int> {
        val step = walkStep
        val angle = 2 * Math.PI * (step % WALK_STEPS) / WALK_STEPS
        val lat = CEBU_LAT + WALK_RADIUS_DEG * sin(angle)
        // Longitude degrees shrink with latitude, so dividing by cos(lat) keeps
        // the loop round on the ground instead of stretched east-west.
        val lng = CEBU_LNG + WALK_RADIUS_DEG * cos(angle) / cos(Math.toRadians(CEBU_LAT))

        return send(
            type = AlertType.TRACKING_UPDATE,
            lat = lat,
            lng = lng,
            batteryPercent = max(WALK_BATTERY_FLOOR, WALK_BATTERY_START - step / 2)
        ).onSuccess { walkStep = step + 1 }
    }

    /** Resets the walk to the start of the loop. */
    fun resetWalk() {
        walkStep = 0
    }

    /**
     * History is written before `latest` on purpose. The Alert tab derives
     * everything it shows from history, so a half-finished send that got as
     * far as history is still visible there and only leaves the Track-facing
     * `latest` node stale. The reverse - `latest` written, history missing -
     * would show the alert nowhere in the Alert tab and leave a permanent hole
     * in the history. For the same reason the sequence id is consumed as soon
     * as its history row lands, so a retry cannot overwrite a good row.
     */
    private suspend fun send(
        type: AlertType,
        lat: Double,
        lng: Double,
        batteryPercent: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sequenceId = nextSequenceId ?: (readHighestHistoryKey() + 1)
            val payload = buildPayload(type, sequenceId, lat, lng, batteryPercent)

            putJson("devices/$DEVICE_ID/history/$sequenceId.json", payload)
            nextSequenceId = sequenceId + 1
            putJson("devices/$DEVICE_ID/latest.json", payload)

            Result.success(sequenceId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The highest sequenceId already in history, or 0 when the band has never
     * reported. Read once per harness instance, then cached in
     * [nextSequenceId].
     *
     * Uses `shallow=true`, which returns the keys alone, rather than ordering
     * by key and taking the last one. RTDB sorts integer-like keys ahead of
     * string keys, so a limitToLast of 1 would hand back a *string* key if the
     * node ever picked one up from the Console - the one case this read exists
     * to survive. Taking the max over the integer-parseable keys cannot be
     * fooled that way, and it skips downloading a payload.
     *
     * A missing node returns the body `null`, not an error, so only a genuine
     * read failure throws - and that is worth failing the send for, because
     * the PUT was about to fail the same way.
     */
    private fun readHighestHistoryKey(): Int {
        val url = "$databaseUrl/devices/$DEVICE_ID/history.json".toHttpUrl()
            .newBuilder()
            .addQueryParameter("shallow", "true")
            .build()

        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} reading the history keys")
            }
            val body = response.body?.string()?.trim()
            if (body.isNullOrEmpty() || body == "null") return 0

            val keys = JSONObject(body).keys()
            var highest = 0
            while (keys.hasNext()) {
                val numeric = keys.next().toIntOrNull() ?: continue
                if (numeric > highest) highest = numeric
            }
            return highest
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

    private fun buildPayload(
        type: AlertType,
        sequenceId: Int,
        lat: Double,
        lng: Double,
        batteryPercent: Int
    ): String {
        val isCharging = Random.nextBoolean()
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

    companion object {
        /** Cebu City, matching the example coordinates in SCHEMA.md. */
        const val CEBU_LAT = 10.3157
        const val CEBU_LNG = 123.8854

        /**
         * The walk loop: [WALK_STEPS] positions around a circle of
         * [WALK_RADIUS_DEG] degrees, roughly 165 m - far enough for the marker
         * to move visibly at street zoom, small enough to stay in Cebu City.
         */
        private const val WALK_STEPS = 24
        private const val WALK_RADIUS_DEG = 0.0015
        private const val WALK_BATTERY_START = 95
        private const val WALK_BATTERY_FLOOR = 20

        private val JSON = "application/json".toMediaType()
        private const val DEVICE_ID = DeviceConstants.DEFAULT_DEVICE_ID
    }
}
