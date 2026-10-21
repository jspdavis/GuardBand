package com.example.guardband.ui.mocksender

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.guardband.R
import com.example.guardband.data.model.AlertType
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
 * **Battery:** Real level and charging state from [BatteryManager] via the
 * sticky [Intent.ACTION_BATTERY_CHANGED] broadcast.
 *
 * Location permissions are declared only in `app/src/debug/AndroidManifest.xml`
 * so release builds stay clean. The receiver app never requests location.
 *
 * View IDs: btnSimulatePanic, btnSimulateCheckin, btnSimulateLowBattery,
 *            btnSimulateTracking, btnWalk, tvStatus, tvGpsStatus.
 */
class MockSenderActivity : AppCompatActivity() {

    private lateinit var btnSimulatePanic: Button
    private lateinit var btnSimulateCheckin: Button
    private lateinit var btnDoubleCheckin: Button
    private lateinit var btnSimulateLowBattery: Button
    private lateinit var btnSimulateTracking: Button
    private lateinit var btnWalk: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvGpsStatus: TextView

    private val alertSender = AlertSender()

    /** Non-null only while the walk is running; see [onPause]. */
    private var walkJob: Job? = null

    // ── Double-press check-in (FR-10) ─────────────────────────────────────────

    /**
     * Timestamp of the first tap of a double-press sequence, or null when no
     * press is pending. Set on the first tap; cleared when the second tap
     * fires or the 2-second window expires.
     */
    private var firstPressTime: Long? = null

    /** Coroutine that resets [firstPressTime] after [DOUBLE_PRESS_WINDOW_MS]. */
    private var doublePressCancelJob: Job? = null

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
        setupClickListeners()
        requestLocationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdatesIfPermitted()
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)

        if (walkJob != null) {
            stopWalk()
            tvStatus.text = getString(R.string.mock_sender_walk_paused)
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
        btnSimulatePanic = findViewById(R.id.btnSimulatePanic)
        btnSimulateCheckin = findViewById(R.id.btnSimulateCheckin)
        btnDoubleCheckin = findViewById(R.id.btnDoubleCheckin)
        btnSimulateLowBattery = findViewById(R.id.btnSimulateLowBattery)
        btnSimulateTracking = findViewById(R.id.btnSimulateTracking)
        btnWalk = findViewById(R.id.btnWalk)
        tvStatus = findViewById(R.id.tvStatus)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
    }

    private fun setupClickListeners() {
        btnSimulatePanic.setOnClickListener { sendAlert(AlertType.PANIC) }
        btnSimulateCheckin.setOnClickListener { sendAlert(AlertType.CHECKIN) }
        btnSimulateLowBattery.setOnClickListener { sendAlert(AlertType.LOW_BATTERY) }
        btnSimulateTracking.setOnClickListener { sendAlert(AlertType.TRACKING_UPDATE) }
        btnDoubleCheckin.setOnClickListener { onDoubleCheckinPressed() }

        btnWalk.setOnClickListener {
            if (walkJob == null) startWalk() else {
                stopWalk()
                tvStatus.text = getString(R.string.mock_sender_walk_stopped)
            }
        }
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

    private fun readIsCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            else -> false
        }
    }

    // ── Double-press check-in (FR-10) ─────────────────────────────────────────

    /**
     * Models the band's FR-10 double-press behaviour: two presses within
     * [DOUBLE_PRESS_WINDOW_MS] (2 s) fire a CHECKIN. A single press that is
     * not followed by a second press within the window resets and shows a
     * "missed" message.
     *
     * State machine:
     *   Idle → first tap → armed (shows countdown prompt, starts timeout)
     *   Armed → second tap within 2 s → send CHECKIN → idle
     *   Armed → timeout after 2 s → missed → idle
     */
    private fun onDoubleCheckinPressed() {
        val now = System.currentTimeMillis()
        val firstTime = firstPressTime

        if (firstTime != null && now - firstTime <= DOUBLE_PRESS_WINDOW_MS) {
            // Second press within the window — fire the CHECKIN.
            doublePressCancelJob?.cancel()
            doublePressCancelJob = null
            firstPressTime = null
            sendAlert(AlertType.CHECKIN)
        } else {
            // First press (or a stale first press just expired) — arm the window.
            doublePressCancelJob?.cancel()
            firstPressTime = now
            tvStatus.text = getString(R.string.mock_sender_checkin_armed)

            // Schedule a reset if the second press doesn't arrive in time.
            doublePressCancelJob = lifecycleScope.launch {
                delay(DOUBLE_PRESS_WINDOW_MS)
                if (firstPressTime != null) {
                    firstPressTime = null
                    tvStatus.text = getString(R.string.mock_sender_checkin_missed)
                }
            }
        }
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    private fun sendAlert(type: AlertType) {
        tvStatus.text = "Sending ${type.name}..."

        val fix = lastFix
        val battery = readBatteryPercent()
        val charging = readIsCharging()

        lifecycleScope.launch {
            val result = alertSender.sendAlert(
                type = type,
                lat = fix?.latitude,
                lng = fix?.longitude,
                accuracy = fix?.accuracy?.toDouble(),
                batteryPercent = battery,
                isCharging = charging
            )

            result.onSuccess { sequenceId ->
                val source = if (fix != null) "real GPS" else "simulated"
                val batteryStr = if (battery != null) "$battery%" else "?"
                tvStatus.text = "✓ Sent ${type.name} as #$sequenceId ($source · 🔋$batteryStr)"
            }.onFailure { error ->
                tvStatus.text = "✗ Failed: ${error.message}"
            }
        }
    }

    private fun startWalk() {
        alertSender.resetWalk()
        btnWalk.text = getString(R.string.mock_sender_walk_stop)

        walkJob = lifecycleScope.launch {
            var sent = 0
            while (isActive) {
                val battery = readBatteryPercent()
                val charging = readIsCharging()

                // Read lastFix fresh on every step. When a real GPS fix is
                // available, pass it directly so the walk sends actual phone
                // coordinates. When there is no fix, null triggers the
                // synthetic circle fallback in AlertSender.
                val currentFix = lastFix

                val result = alertSender.sendWalkStep(
                    lat = currentFix?.latitude,
                    lng = currentFix?.longitude,
                    accuracy = currentFix?.accuracy?.toDouble(),
                    originLat = currentFix?.latitude ?: AlertSender.WALK_ORIGIN_LAT,
                    originLng = currentFix?.longitude ?: AlertSender.WALK_ORIGIN_LNG,
                    batteryPercent = battery,
                    isCharging = charging
                )

                result.onSuccess { sequenceId ->
                    sent++
                    val source = if (currentFix != null) "real GPS" else "simulated"
                    val batteryStr = if (battery != null) "$battery%" else "?"
                    tvStatus.text = "⚑ Walking: $sent sent, latest #$sequenceId ($source · 🔋$batteryStr)"
                }.onFailure { error ->
                    tvStatus.text = "✗ Walk stopped after $sent: ${error.message}"
                    stopWalk()
                    return@launch
                }

                delay(WALK_INTERVAL_MS)
            }
        }
    }

    private fun stopWalk() {
        walkJob?.cancel()
        walkJob = null
        btnWalk.text = getString(R.string.mock_sender_walk_start)
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val WALK_INTERVAL_MS = 5_000L

        /** Request a new fix at most every 1 s; fire on any movement. */
        const val GPS_MIN_TIME_MS = 1_000L
        const val GPS_MIN_DISTANCE_M = 0f

        const val REQUEST_LOCATION = 1001

        /**
         * FR-10: the band sends CHECKIN on a double-press within this window.
         * Two taps on [btnDoubleCheckin] within 2 s fire the alert; a single
         * tap that times out resets and shows a missed message.
         */
        const val DOUBLE_PRESS_WINDOW_MS = 2_000L

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
