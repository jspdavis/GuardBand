package com.example.guardband.ui.dependent

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.alert.AlertFormatting
import com.example.guardband.ui.alert.AlertViewModel
import com.example.guardband.ui.home.HomeViewModel
import com.example.guardband.ui.track.BandStatus
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Guardian's "Dependent" tab: shows the dependent person's status, band telemetry,
 * connection state, and quick action to view the full incident history timeline.
 */
class DependentStatusFragment : Fragment(R.layout.fragment_dependent_status) {

    private lateinit var tvConnection: TextView
    private lateinit var tvBattery: TextView
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var tvLocation: TextView
    private lateinit var tvLatestSummary: TextView
    private lateinit var btnViewTimeline: MaterialButton

    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }
    private val alertViewModel: AlertViewModel by activityViewModels { AlertViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_dependent_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<ImageButton>(R.id.ib_dependent_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }

        tvConnection    = view.findViewById(R.id.tv_dep_connection)
        tvBattery       = view.findViewById(R.id.tv_dep_battery)
        ivBatteryIcon   = view.findViewById(R.id.iv_dep_battery_icon)
        tvLocation      = view.findViewById(R.id.tv_dep_location)
        tvLatestSummary = view.findViewById(R.id.tv_dep_latest_alert_summary)
        btnViewTimeline = view.findViewById(R.id.btn_dep_view_timeline)

        btnViewTimeline.setOnClickListener {
            homeViewModel.onSeeAllAlertsClicked()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                alertViewModel.uiState.collect { state ->
                    val newest = state.newestReport
                    if (newest != null) {
                        tvConnection.text = getString(R.string.label_device_status_reporting)
                        val battery = newest.battery
                        if (battery != null) {
                            tvBattery.text = getString(R.string.label_alert_battery, battery.percent)
                            ivBatteryIcon.setImageResource(AlertFormatting.batteryIcon(battery))
                        }
                        val placeName = state.placeNameFor(newest)
                        val loc = newest.location
                        tvLocation.text = if (loc != null) {
                            AlertFormatting.locationText(requireContext(), loc, placeName)
                        } else {
                            "Coordinates unavailable"
                        }
                        val latest = state.latest
                        if (latest != null) {
                            tvLatestSummary.text = "Latest incident: ${latest.type} (${state.rowsOf(listOf(latest)).first().status})"
                        }
                    } else {
                        tvConnection.text = "Waiting for data..."
                    }
                }
            }
        }
    }
}
