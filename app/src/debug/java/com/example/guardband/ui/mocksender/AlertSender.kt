package com.example.guardband.ui.mocksender

import com.example.guardband.data.DeviceConstants
import com.example.guardband.data.FirebaseProvider
import com.example.guardband.data.model.AlertType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * One send at a time. A check-in pressed while a tracking step is in
     * flight would otherwise read the same [nextSequenceId] and overwrite that
     * step's history row. The band sends one message at a time too.
     */
    private val sendLock = Mutex()

    /**
     * The id the next alert will use, or null until the first send seeds it
     * from whatever is already in the database. Seeding from the server, rather
     * than from 1, is what stops a relaunched harness from overwriting the
     * history rows it wrote last time.
     */
    private var nextSequenceId: Int? = null

    /** How many tracking steps have been sent, which is what moves the marker. */
    private var trackingStep = 0

    private val databaseUrl: String by lazy {
        FirebaseProvider.database.reference.toString().trimEnd('/')
    }

    /**
     * Writes one alert of [type].
     *
     * When [lat], [lng] and [accuracy] are supplied (from Phone A's GPS fix)
     * they are used directly and reported as a real fix in the status line.
     * When they are null the sender falls back to a random point within ±0.01°
     * of Cebu City, preserving the original behaviour for indoor demos or when
     * the permission has been denied.
     *
     * [accuracy] maps to `location.accuracyMeters` in the SCHEMA.md payload.
     * When a real fix is used, pass `android.location.Location.accuracy`
     * (metres, float); when falling back, a random 5–15 m value is used.
     *
     * [batteryPercent] and [isCharging] should come from the phone's
     * [android.os.BatteryManager]. When null, random values are used as a
     * fallback so the payload remains valid (battery is required by SCHEMA.md).
     *
     * @return the sequenceId that was written, or the failure that stopped it
     */
    suspend fun sendAlert(
        type: AlertType,
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Double? = null,
        batteryPercent: Int? = null,
        isCharging: Boolean? = null
    ): Result<Int> {
        val resolvedLat = lat ?: (CEBU_LAT + Random.nextDouble(-0.01, 0.01))
        val resolvedLng = lng ?: (CEBU_LNG + Random.nextDouble(-0.01, 0.01))
        val resolvedAccuracy = accuracy ?: Random.nextDouble(5.0, 15.0)
        val resolvedBattery = batteryPercent ?: Random.nextInt(20, 101)
        val resolvedCharging = isCharging ?: Random.nextBoolean()
        return send(resolvedLat, resolvedLng, type, resolvedBattery, resolvedCharging, resolvedAccuracy)
    }

    /**
     * Writes one TRACKING_UPDATE.
     *
     * When [lat], [lng] and [accuracy] are supplied (real GPS fix), they are
     * sent directly — the marker moves to the phone's actual position on each
     * tick. This is the preferred path when GPS is available (accuracy ≤ ~20 m).
     *
     * When they are null (no fix, or indoors), the sender falls back to the
     * synthetic circle: [LOOP_STEPS] positions around a ~165 m loop anchored
     * at [originLat]/[originLng]. The circle is kept so indoor demos still show
     * a moving marker even without GPS.
     */
    suspend fun sendTrackingStep(
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Double? = null,
        originLat: Double = LOOP_ORIGIN_LAT,
        originLng: Double = LOOP_ORIGIN_LNG,
        batteryPercent: Int? = null,
        isCharging: Boolean? = null
    ): Result<Int> {
        val resolvedBattery = batteryPercent ?: max(LOOP_BATTERY_FLOOR, LOOP_BATTERY_START - trackingStep / 2)
        val resolvedCharging = isCharging ?: false

        return if (lat != null && lng != null && accuracy != null) {
            // Real GPS available — send the actual position directly.
            send(
                lat = lat,
                lng = lng,
                type = AlertType.TRACKING_UPDATE,
                batteryPercent = resolvedBattery,
                isCharging = resolvedCharging,
                accuracyMeters = accuracy
            ).onSuccess { trackingStep++ }
        } else {
            // No fix — fall back to synthetic circle for indoor demos.
            val step = trackingStep
            val angle = 2 * Math.PI * (step % LOOP_STEPS) / LOOP_STEPS
            val circleLat = originLat + LOOP_RADIUS_DEG * sin(angle)
            // Longitude degrees shrink with latitude; dividing by cos(lat) keeps
            // the loop round on the ground instead of stretched east-west.
            val circleLng = originLng + LOOP_RADIUS_DEG * cos(angle) / cos(Math.toRadians(originLat))
            send(
                lat = circleLat,
                lng = circleLng,
                type = AlertType.TRACKING_UPDATE,
                batteryPercent = resolvedBattery,
                isCharging = resolvedCharging,
                accuracyMeters = LOOP_SYNTHETIC_ACCURACY
            ).onSuccess { trackingStep = step + 1 }
        }
    }

    /** Resets tracking to the start of the synthetic loop. */
    fun resetTracking() {
        trackingStep = 0
    }

    /**
     * History is written before `latest` on purpose. The alert screens (Track's
     * recent-alerts panel and Alert history) derive everything from history, so a half-finished send that got as
     * far as history is still visible there and only leaves the Track-facing
     * `latest` node stale. The reverse - `latest` written, history missing -
     * would show the alert on neither of them and leave a permanent hole
     * in the history. For the same reason the sequence id is consumed as soon
     * as its history row lands, so a retry cannot overwrite a good row.
     */
    private suspend fun send(
        lat: Double,
        lng: Double,
        type: AlertType,
        batteryPercent: Int,
        isCharging: Boolean,
        accuracyMeters: Double
    ): Result<Int> = sendLock.withLock { sendNow(lat, lng, type, batteryPercent, isCharging, accuracyMeters) }

    private suspend fun sendNow(
        lat: Double,
        lng: Double,
        type: AlertType,
        batteryPercent: Int,
        isCharging: Boolean,
        accuracyMeters: Double
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sequenceId = nextSequenceId ?: (readHighestHistoryKey() + 1)
            val payload = buildPayload(type, sequenceId, lat, lng, batteryPercent, isCharging, accuracyMeters)

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
        batteryPercent: Int,
        isCharging: Boolean,
        accuracyMeters: Double
    ): String {
        return """
        {
            "schemaVersion": "1.0",
            "deviceId": "$DEVICE_ID",
            "type": "${type.name}",
            "timestamp": "${isoUtcNow()}",
            "location": {
                "lat": $lat,
                "lng": $lng,
                "accuracyMeters": $accuracyMeters
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
        /** Cebu City centre — fallback when no GPS fix is available. */
        const val CEBU_LAT = 10.3157
        const val CEBU_LNG = 123.8854

        /**
         * Synthetic-loop origin. Defaults to Cebu City so a demo without a real fix
         * behaves exactly as before. [MockSenderActivity] passes the phone's
         * real fix here when one is available, anchoring the loop around the
         * actual location instead of the hardcoded city centre.
         */
        const val LOOP_ORIGIN_LAT = CEBU_LAT
        const val LOOP_ORIGIN_LNG = CEBU_LNG

        /**
         * The synthetic loop, used when there is no GPS fix: [LOOP_STEPS] positions around a circle of
         * [LOOP_RADIUS_DEG] degrees, roughly 165 m — far enough for the marker
         * to move visibly at street zoom, small enough to stay near the origin.
         *
         * The loop uses a fixed synthetic accuracy rather than GPS, because the
         * loop is deterministic, not a sequence of real fixes.
         */
        private const val LOOP_STEPS = 24
        private const val LOOP_RADIUS_DEG = 0.0015
        private const val LOOP_BATTERY_START = 95
        private const val LOOP_BATTERY_FLOOR = 20
        private const val LOOP_SYNTHETIC_ACCURACY = 10.0

        private val JSON = "application/json".toMediaType()
        private const val DEVICE_ID = DeviceConstants.DEFAULT_DEVICE_ID
    }
}
