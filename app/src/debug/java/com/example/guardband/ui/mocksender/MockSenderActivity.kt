package com.example.guardband.ui.mocksender

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.guardband.R
import com.example.guardband.data.model.AlertType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MockSenderActivity - Temporary test harness activity for simulating GuardBand device alerts.
 *
 * This activity stands in for ESP32 firmware and should be considered scaffolding/disposable
 * once real hardware exists. It deliberately avoids the MVVM pattern since it's not part of
 * the permanent app architecture.
 *
 * Each button writes one alert straight to the Realtime Database through
 * [AlertSender]; the status line reports the history key it landed under, so a
 * send can be checked against the Console.
 *
 * "Walk" sends a TRACKING_UPDATE every [WALK_INTERVAL_MS] along a loop around
 * Cebu City, which is what makes the Track marker move. It stops in [onPause]
 * rather than running on under a backgrounded harness: a walk that kept writing
 * while nobody was looking would make a dead band look alive.
 */
class MockSenderActivity : AppCompatActivity() {

    private lateinit var btnSimulatePanic: Button
    private lateinit var btnSimulateCheckin: Button
    private lateinit var btnSimulateLowBattery: Button
    private lateinit var btnSimulateTracking: Button
    private lateinit var btnWalk: Button
    private lateinit var tvStatus: TextView

    private val alertSender = AlertSender()

    /** Non-null only while the walk is running; see [onPause]. */
    private var walkJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mock_sender)

        initViews()
        setupClickListeners()
    }

    override fun onPause() {
        super.onPause()
        if (walkJob != null) {
            stopWalk()
            tvStatus.text = getString(R.string.mock_sender_walk_paused)
        }
    }

    private fun initViews() {
        btnSimulatePanic = findViewById(R.id.btnSimulatePanic)
        btnSimulateCheckin = findViewById(R.id.btnSimulateCheckin)
        btnSimulateLowBattery = findViewById(R.id.btnSimulateLowBattery)
        btnSimulateTracking = findViewById(R.id.btnSimulateTracking)
        btnWalk = findViewById(R.id.btnWalk)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupClickListeners() {
        btnSimulatePanic.setOnClickListener {
            sendAlert(AlertType.PANIC)
        }

        btnSimulateCheckin.setOnClickListener {
            sendAlert(AlertType.CHECKIN)
        }

        btnSimulateLowBattery.setOnClickListener {
            sendAlert(AlertType.LOW_BATTERY)
        }

        btnSimulateTracking.setOnClickListener {
            sendAlert(AlertType.TRACKING_UPDATE)
        }

        btnWalk.setOnClickListener {
            if (walkJob == null) startWalk() else {
                stopWalk()
                tvStatus.text = getString(R.string.mock_sender_walk_stopped)
            }
        }
    }

    private fun sendAlert(type: AlertType) {
        tvStatus.text = "Sending ${type.name}..."

        lifecycleScope.launch {
            val result = alertSender.sendAlert(type)

            result.onSuccess { sequenceId ->
                // The sequenceId is shown because it is the history key the
                // Alert tab reads, so a demo can be checked against the Console.
                tvStatus.text = "✓ Sent ${type.name} as #$sequenceId"
            }.onFailure { error ->
                tvStatus.text = "✗ Failed: ${error.message}"
            }
        }
    }

    /**
     * Sends TRACKING_UPDATEs on a timer until stopped.
     *
     * A failed step **stops the walk** rather than carrying on: once a write is
     * being refused, every later step would be refused the same way, and a
     * status line rewriting the same error every few seconds hides which step
     * first broke.
     */
    private fun startWalk() {
        alertSender.resetWalk()
        btnWalk.text = getString(R.string.mock_sender_walk_stop)

        walkJob = lifecycleScope.launch {
            var sent = 0
            while (isActive) {
                val result = alertSender.sendWalkStep()

                result.onSuccess { sequenceId ->
                    sent++
                    tvStatus.text = "⚑ Walking: $sent sent, latest #$sequenceId"
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

    private companion object {
        /**
         * Deliberately far faster than FR-09's real 60 s tracking interval: a
         * demo should show the marker moving within seconds, and anything under
         * the Track tab's staleness threshold keeps the band reading as online.
         */
        const val WALK_INTERVAL_MS = 5_000L
    }
}
