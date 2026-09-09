package com.example.guardband.ui.mocksender

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.guardband.R
import kotlinx.coroutines.launch

/**
 * MockSenderActivity - Temporary test harness activity for simulating GuardBand device alerts.
 * 
 * This activity stands in for ESP32 firmware and should be considered scaffolding/disposable
 * once real hardware exists. It deliberately avoids the MVVM pattern since it's not part of
 * the permanent app architecture.
 */
class MockSenderActivity : AppCompatActivity() {

    private lateinit var btnSimulatePanic: Button
    private lateinit var btnSimulateCheckin: Button
    private lateinit var btnSimulateLowBattery: Button
    private lateinit var btnSimulateTracking: Button
    private lateinit var tvStatus: TextView

    private val alertSender = AlertSender()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mock_sender)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnSimulatePanic = findViewById(R.id.btnSimulatePanic)
        btnSimulateCheckin = findViewById(R.id.btnSimulateCheckin)
        btnSimulateLowBattery = findViewById(R.id.btnSimulateLowBattery)
        btnSimulateTracking = findViewById(R.id.btnSimulateTracking)
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
    }

    private fun sendAlert(type: AlertType) {
        tvStatus.text = "Sending ${type.name}..."
        
        lifecycleScope.launch {
            val result = alertSender.sendAlert(type)
            
            result.onSuccess {
                tvStatus.text = "✓ Sent ${type.name} successfully"
            }.onFailure { error ->
                tvStatus.text = "✗ Failed: ${error.message}"
            }
        }
    }
}
