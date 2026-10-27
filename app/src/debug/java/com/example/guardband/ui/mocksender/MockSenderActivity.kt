package com.example.guardband.ui.mocksender

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.guardband.R
import com.example.guardband.data.model.AlertType
import com.example.guardband.ui.mocksender.BandButtonDecoder.Gesture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MockSenderActivity — Temporary test harness activity for simulating GuardBand device alerts.
 *
 * This activity stands in for ESP32 firmware and should be considered scaffolding/disposable
 * once real hardware exists. It deliberately avoids the MVVM pattern since it is not part of
 * the permanent app architecture.
 *
 * **It behaves like the band (2026-10-27):** one button, decoded by
 * [BandButtonDecoder] with the firmware's timings, and a battery slider.
 *
 *  - **Hold 1.5 s → PANIC**, sent the moment the hold reaches it, then
 *    **tracking**: a TRACKING_UPDATE every [TRACKING_INTERVAL_MS] (5 s) for
 *    [TRACKING_DURATION_MS] (30 min, FR-09).
 *  - **Double-press within 2 s → CHECKIN** (FR-10). It does not stop
 *    tracking: a check-in can be forced, a cancel should not be easy to.
 *  - **During a panic, hold 5 s → cancel**: tracking stops and a CHECKIN is
 *    sent, so the guardian sees the wearer says they are OK.
 *  - **A single press sends nothing.**
 *  - **The battery slider** is the level every message reports (never
 *    charging: the band is on a wrist). Dropping it to 20% or below sends a
 *    LOW_BATTERY by itself (FR-11), once, until it goes back above 20%.
 *
 * Tracking stops when the harness leaves the foreground, as the old walk did:
 * a background loop writing under a backgrounded harness would make a dead
 * band look alive.
 *
 * **Location strategy:**
 * Uses [LocationManager] directly — no FusedLocationProviderClient, no Play Services
 * in the path. This is more reliable on devices where battery optimisation or
 * power-saving location mode silences the fused provider.
 *
 * On every resume:
 *  1. Reads [LocationManager.getLastKnownLocation] synchronously from GPS,
 *     network and passive providers — gives an immediate fix before the first
 *     button tap.
 *  2. Registers a [LocationListener] on GPS_PROVIDER (most accurate) and
 *     NETWORK_PROVIDER (fast updates indoors/between GPS pulses). Every update
 *     from either provider is accepted unconditionally; the hardware chose it.
 *
 * On pause, both listeners are unregistered (no background updates).
 *
 * Location permissions are declared only in `app/src/debug/AndroidManifest.xml`
 * so release builds stay clean. The receiver app never requests location.
 *
 * View IDs: btnBand, tvTracking, tvBattery, sbBattery, tvStatus, tvGpsStatus.
 */
class MockSenderActivity : AppCompatActivity() {

    private lateinit var btnBand: TextView
    private lateinit var tvTracking: TextView
    private lateinit var tvBattery: TextView
    private lateinit var sbBattery: SeekBar
    private lateinit var tvStatus: TextView
    private lateinit var tvGpsStatus: TextView

    private val alertSender = AlertSender()

    // ── The band's button and battery ─────────────────────────────────────────

    private val decoder = BandButtonDecoder()

    /** Fires the hold's gesture when it reaches its time; cancelled on release. */
    private var holdJob: Job? = null

    /** During a panic, the "keep holding" hint at 1.5 s. */
    private var holdHintJob: Job? = null

    /** Ends a lone first press once the double-press window is over. */
    private var windowJob: Job? = null

    /** The panic's tracking; non-null only while it runs. See [onPause]. */
    private var trackingJob: Job? = null

    /** The level every message reports, from the slider. */
    private var batteryPercent = 100

    /** Whether the next drop to [LOW_BATTERY_PERCENT] sends a LOW_BATTERY. */
    private var lowBatteryArmed = true

    // ── Location ──────────────────────────────────────────────────────────────

    private lateinit var locationManager: LocationManager

    /**
     * Best available fix. Survives pause/resume so the badge never blanks.
     * Updated by the synchronous seed and by both location listeners.
     */
    private var lastFix: Location? = null

    private var locationPermissionDenied = false

    /**
     * Single listener used for both GPS_PROVIDER and NETWORK_PROVIDER.
     *
     * Only replaces [lastFix] when the incoming fix is genuinely better:
     * more accurate, or newer when accuracy is similar. This prevents a
     * frequent coarse NETWORK_PROVIDER update (±100–300 m) from silently
     * overwriting a precise GPS fix (±5–15 m) that arrived earlier.
     */
    private val locationListener = LocationListener { location ->
        if (isBetterFix(location, lastFix)) {
            lastFix = location
            updateGpsStatus()
        }
    }

    /**
     * True when [candidate] should replace [current] in the live listener.
     *
     * Rules (applied in order):
     *  1. No current fix → always accept.
     *  2. Candidate comes from GPS_PROVIDER → always accept. A live GPS fix
     *     is always fresher and more accurate than a cached network fix; we
     *     never want network to win over a real satellite lock.
     *  3. Candidate is meaningfully more accurate (≥[ACCURACY_IMPROVEMENT_M])
     *     → accept regardless of provider.
     *  4. Current fix is stale (>[FRESHNESS_MS]) and candidate is not wildly
     *     less accurate → accept to stay current.
     */
    private fun isBetterFix(candidate: Location, current: Location?): Boolean {
        if (current == null) return true
        // A live GPS fix always wins — it has a satellite lock right now.
        if (candidate.provider == LocationManager.GPS_PROVIDER) return true
        // A meaningfully more accurate fix wins.
        val accuracyGain = current.accuracy - candidate.accuracy
        if (accuracyGain >= ACCURACY_IMPROVEMENT_M) return true
        // A significantly newer fix wins when accuracy doesn't degrade too much.
        val ageDiff = candidate.time - current.time
        if (ageDiff > FRESHNESS_MS &&
            candidate.accuracy <= current.accuracy * ACCURACY_DEGRADATION_FACTOR) return true
        return false
    }


    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mock_sender)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        initViews()
        setUpBandButton()
        setUpBatterySlider()
        requestLocationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdatesIfPermitted()
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)

        if (trackingJob != null) {
            stopTracking()
            tvTracking.text = getString(R.string.mock_sender_tracking_paused)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            locationPermissionDenied = false
            startLocationUpdatesIfPermitted()
        } else {
            locationPermissionDenied = true
            lastFix = null
            updateGpsStatus()
        }
    }


    // ── Views ─────────────────────────────────────────────────────────────────

    private fun initViews() {
        btnBand = findViewById(R.id.btnBand)
        tvTracking = findViewById(R.id.tvTracking)
        tvBattery = findViewById(R.id.tvBattery)
        sbBattery = findViewById(R.id.sbBattery)
        tvStatus = findViewById(R.id.tvStatus)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
    }

    /**
     * The band's one button. The raw touch is read, rather than click and
     * long-click listeners, because the hold has to fire at exactly 1.5 s
     * while still held, and 5 s during a panic. The ScrollView is kept from
     * taking the touch, so a hold is never cancelled by a slight drag.
     *
     * TalkBack users cannot time a hold or a double-press, so the same three
     * actions are also offered as accessibility actions.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setUpBandButton() {
        btnBand.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.isPressed = true
                    onBandDown()
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    onBandUp()
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    cancelHoldTimers()
                    decoder.cancel()
                }
            }
            true
        }

        ViewCompat.addAccessibilityAction(btnBand, getString(R.string.mock_sender_action_panic)) { _, _ ->
            if (trackingJob == null) perform(Gesture.PANIC)
            true
        }
        ViewCompat.addAccessibilityAction(btnBand, getString(R.string.mock_sender_action_check_in)) { _, _ ->
            perform(Gesture.CHECK_IN)
            true
        }
        ViewCompat.addAccessibilityAction(btnBand, getString(R.string.mock_sender_action_cancel)) { _, _ ->
            if (trackingJob != null) perform(Gesture.CANCEL_PANIC)
            true
        }
    }

    /**
     * Starts on the phone's own battery level, so the first messages look
     * like before; from then on the slider decides. Starting at or below 20%
     * does not send a LOW_BATTERY: only a drop to it does.
     */
    private fun setUpBatterySlider() {
        batteryPercent = readBatteryPercent() ?: 100
        lowBatteryArmed = batteryPercent > LOW_BATTERY_PERCENT
        sbBattery.progress = batteryPercent
        renderBattery()

        sbBattery.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                batteryPercent = progress
                if (progress > LOW_BATTERY_PERCENT) lowBatteryArmed = true
                renderBattery()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            // On release, not while dragging, so a drag through 20% on its way
            // down to 5% sends one alert, at the level let go at.
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (batteryPercent <= LOW_BATTERY_PERCENT && lowBatteryArmed) {
                    lowBatteryArmed = false
                    renderBattery()
                    sendAlert(AlertType.LOW_BATTERY)
                }
            }
        })
    }

    private fun renderBattery() {
        tvBattery.text = getString(
            if (batteryPercent <= LOW_BATTERY_PERCENT && !lowBatteryArmed) R.string.mock_sender_battery_low
            else R.string.mock_sender_battery,
            batteryPercent
        )
    }

    // ── Location helpers ──────────────────────────────────────────────────────

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION
        )
    }

    /**
     * Seeds [lastFix] synchronously from the LocationManager cache, then
     * registers live listeners on GPS_PROVIDER and NETWORK_PROVIDER.
     *
     * The synchronous seed means the badge always shows something real
     * immediately, even before the first live update fires. The GPS listener
     * will replace it with a precise fix once the hardware acquires satellites
     * (typically 10–30 s outdoors); the network listener bridges that gap
     * with a cell/WiFi fix in the meantime.
     */
    @Suppress("MissingPermission") // guarded by hasLocationPermission()
    private fun startLocationUpdatesIfPermitted() {
        if (!hasLocationPermission()) {
            updateGpsStatus()
            return
        }

        // ── Synchronous seed from cache ───────────────────────────────────────
        // Collect all available cached fixes and pick the freshest one.
        // We prioritise recency over accuracy here: a fresh ±150 m network
        // fix from 10 s ago is more useful than a precise ±10 m GPS fix
        // cached from 2 hours ago with wrong coordinates.
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var freshestCached: Location? = null
        for (provider in providers) {
            val cached = runCatching {
                locationManager.getLastKnownLocation(provider)
            }.getOrNull() ?: continue
            if (freshestCached == null || cached.time > freshestCached.time) {
                freshestCached = cached
            }
        }
        if (freshestCached != null && (lastFix == null || freshestCached.time > lastFix!!.time)) {
            lastFix = freshestCached
        }
        updateGpsStatus()

        // ── Live GPS updates (most accurate) ─────────────────────────────────
        // minTime=1s, minDistance=0 — fire on every fix regardless of movement.
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_MIN_TIME_MS,
                GPS_MIN_DISTANCE_M,
                locationListener
            )
        }

        // ── Live network updates (fast, good indoors) ─────────────────────────
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                GPS_MIN_TIME_MS,
                GPS_MIN_DISTANCE_M,
                locationListener
            )
        }
    }

    /** Refreshes the GPS badge below the status line. */
    private fun updateGpsStatus() {
        tvGpsStatus.text = when {
            locationPermissionDenied ->
                getString(R.string.mock_sender_gps_denied)
            lastFix != null -> {
                val fix = lastFix!!
                getString(
                    R.string.mock_sender_gps_fix,
                    fix.latitude,
                    fix.longitude,
                    fix.accuracy.toDouble()
                )
            }
            hasLocationPermission() ->
                getString(R.string.mock_sender_gps_acquiring)
            else ->
                getString(R.string.mock_sender_gps_no_fix)
        }
    }

    // ── Battery helpers ───────────────────────────────────────────────────────

    private fun readBatteryPercent(): Int? {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }

    // ── The band's button ─────────────────────────────────────────────────────

    private fun onBandDown() {
        cancelHoldTimers()
        val tracking = trackingJob != null
        val holdMs = decoder.down(SystemClock.elapsedRealtime(), tracking)
        holdJob = lifecycleScope.launch {
            delay(holdMs)
            decoder.holdReached(SystemClock.elapsedRealtime())?.let(::perform)
        }
        if (tracking) {
            holdHintJob = lifecycleScope.launch {
                delay(decoder.panicHoldMs)
                tvStatus.text = getString(R.string.mock_sender_cancel_hint)
            }
        }
    }

    private fun onBandUp() {
        cancelHoldTimers()
        val gesture = decoder.up(SystemClock.elapsedRealtime()) ?: return
        perform(gesture)
        if (gesture == Gesture.FIRST_PRESS) {
            windowJob?.cancel()
            windowJob = lifecycleScope.launch {
                delay(decoder.doublePressMs)
                decoder.windowLapsed(SystemClock.elapsedRealtime())?.let(::perform)
            }
        }
    }

    private fun cancelHoldTimers() {
        holdJob?.cancel()
        holdJob = null
        holdHintJob?.cancel()
        holdHintJob = null
    }

    /** What the band does for each gesture. */
    private fun perform(gesture: Gesture) {
        when (gesture) {
            Gesture.PANIC -> {
                btnBand.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                lifecycleScope.launch {
                    // Tracking starts once the panic has landed, so the first
                    // position always follows it in the history.
                    if (send(AlertType.PANIC).isSuccess) startTracking()
                }
            }
            Gesture.CHECK_IN -> sendAlert(AlertType.CHECKIN)
            Gesture.CANCEL_PANIC -> {
                btnBand.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                stopTracking()
                tvTracking.text = getString(R.string.mock_sender_tracking_cancelled)
                sendAlert(AlertType.CHECKIN)
            }
            Gesture.FIRST_PRESS -> tvStatus.text = getString(R.string.mock_sender_first_press)
            Gesture.SINGLE_PRESS -> tvStatus.text = getString(R.string.mock_sender_single_press)
            Gesture.CANCEL_TOO_SHORT -> tvStatus.text = getString(R.string.mock_sender_cancel_too_short)
        }
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    private fun sendAlert(type: AlertType) {
        lifecycleScope.launch { send(type) }
    }

    /** Sends one alert at the GPS fix and the slider's battery, and reports it. */
    private suspend fun send(type: AlertType): Result<Int> {
        tvStatus.text = "Sending ${type.name}..."
        val fix = lastFix
        val battery = batteryPercent

        val result = alertSender.sendAlert(
            type = type,
            lat = fix?.latitude,
            lng = fix?.longitude,
            accuracy = fix?.accuracy?.toDouble(),
            batteryPercent = battery,
            isCharging = false
        )

        result.onSuccess { sequenceId ->
            val source = if (fix != null) "real GPS" else "simulated"
            tvStatus.text = "✓ Sent ${type.name} as #$sequenceId ($source · 🔋$battery%)"
        }.onFailure { error ->
            tvStatus.text = "✗ Failed: ${error.message}"
        }
        return result
    }

    // ── Tracking after a panic (FR-09) ────────────────────────────────────────

    /**
     * A TRACKING_UPDATE every [TRACKING_INTERVAL_MS] until
     * [TRACKING_DURATION_MS] after the panic, a cancel, or the harness
     * leaving the screen. Each step reads the GPS fix afresh; with no fix the
     * sender walks its synthetic loop instead, around the last known point.
     * A failed step stops tracking rather than retrying every few seconds.
     */
    private fun startTracking() {
        if (trackingJob != null) return
        alertSender.resetTracking()
        val startedAt = SystemClock.elapsedRealtime()

        trackingJob = lifecycleScope.launch {
            var sent = 0
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= TRACKING_DURATION_MS) {
                    trackingJob = null
                    tvTracking.text = getString(R.string.mock_sender_tracking_done)
                    return@launch
                }

                val fix = lastFix
                val result = alertSender.sendTrackingStep(
                    lat = fix?.latitude,
                    lng = fix?.longitude,
                    accuracy = fix?.accuracy?.toDouble(),
                    originLat = fix?.latitude ?: AlertSender.LOOP_ORIGIN_LAT,
                    originLng = fix?.longitude ?: AlertSender.LOOP_ORIGIN_LNG,
                    batteryPercent = batteryPercent,
                    isCharging = false
                )

                result.onSuccess {
                    sent++
                    val minutesLeft = ((TRACKING_DURATION_MS - elapsed) / 60_000L).toInt()
                    tvTracking.text = getString(R.string.mock_sender_tracking_on, sent, minutesLeft)
                }.onFailure { error ->
                    trackingJob = null
                    tvTracking.text = getString(R.string.mock_sender_tracking_failed, sent, error.message ?: "")
                    return@launch
                }

                delay(TRACKING_INTERVAL_MS)
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        /**
         * How often tracking sends a position after a panic: every 5 s, not
         * FR-09's 60 s, so a demo shows a path within a minute. The status
         * line says so.
         */
        const val TRACKING_INTERVAL_MS = 5_000L

        /** FR-09: tracking runs for 30 minutes after the panic. */
        const val TRACKING_DURATION_MS = 30 * 60_000L

        /** FR-11: the band sends a low-battery alert at 20%. */
        const val LOW_BATTERY_PERCENT = 20

        /** Request a new fix at most every 1 s; fire on any movement. */
        const val GPS_MIN_TIME_MS = 1_000L
        const val GPS_MIN_DISTANCE_M = 0f

        const val REQUEST_LOCATION = 1001

        /**
         * A new fix must be this much more accurate to replace a good GPS fix.
         * 30 m: a GPS fix at 10 m beats a network fix at 150 m (gain = 140 m ≥ 30 m).
         * A network fix at 120 m does NOT beat a GPS fix at 10 m (gain = -110 m < 30 m).
         */
        const val ACCURACY_IMPROVEMENT_M = 30f

        /**
         * After this long, accept a fix even if slightly less accurate —
         * a GPS fix from 2 minutes ago is genuinely stale.
         */
        const val FRESHNESS_MS = 120_000L   // 2 minutes

        /**
         * When accepting a stale replacement by age, don't accept one that is
         * more than 3× less accurate than the current fix.
         * e.g. don't replace a 10 m GPS fix with a 200 m network fix just because
         * it's 2 minutes newer.
         */
        const val ACCURACY_DEGRADATION_FACTOR = 3.0f
    }
}
