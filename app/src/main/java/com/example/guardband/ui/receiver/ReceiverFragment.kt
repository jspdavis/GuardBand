package com.example.guardband.ui.receiver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * Fragment displaying live alerts from Firebase Realtime Database
 * Shows current /latest alert and /history list with real-time updates
 */
class ReceiverFragment : Fragment() {
    private lateinit var viewModel: ReceiverViewModel
    private lateinit var historyAdapter: AlertHistoryAdapter

    // Views
    private lateinit var progressBar: ProgressBar
    private lateinit var latestAlertCard: MaterialCardView
    private lateinit var tvLatestType: TextView
    private lateinit var tvLatestTimestamp: TextView
    private lateinit var tvLatestLocation: TextView
    private lateinit var tvLatestBattery: TextView
    private lateinit var tvLatestSequence: TextView
    private lateinit var tvNoLatestAlert: TextView
    private lateinit var rvAlertHistory: RecyclerView
    private lateinit var tvNoHistory: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_receiver, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ReceiverViewModel::class.java]

        // Initialize views
        initViews(view)

        // Setup RecyclerView
        setupRecyclerView()

        // Observe ViewModel state
        observeViewModel()
    }

    private fun initViews(view: View) {
        progressBar = view.findViewById(R.id.progressBar)
        latestAlertCard = view.findViewById(R.id.latestAlertCard)
        tvLatestType = view.findViewById(R.id.tvLatestType)
        tvLatestTimestamp = view.findViewById(R.id.tvLatestTimestamp)
        tvLatestLocation = view.findViewById(R.id.tvLatestLocation)
        tvLatestBattery = view.findViewById(R.id.tvLatestBattery)
        tvLatestSequence = view.findViewById(R.id.tvLatestSequence)
        tvNoLatestAlert = view.findViewById(R.id.tvNoLatestAlert)
        rvAlertHistory = view.findViewById(R.id.rvAlertHistory)
        tvNoHistory = view.findViewById(R.id.tvNoHistory)
    }

    private fun setupRecyclerView() {
        historyAdapter = AlertHistoryAdapter()
        rvAlertHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe loading state
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }

                // Observe latest alert
                launch {
                    viewModel.latestAlert.collect { alert ->
                        displayLatestAlert(alert)
                    }
                }

                // Observe alert history
                launch {
                    viewModel.alertHistory.collect { history ->
                        displayAlertHistory(history)
                    }
                }

                // Observe errors
                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun displayLatestAlert(alert: Alert?) {
        if (alert == null) {
            latestAlertCard.visibility = View.GONE
            tvNoLatestAlert.visibility = View.VISIBLE
        } else {
            latestAlertCard.visibility = View.VISIBLE
            tvNoLatestAlert.visibility = View.GONE

            tvLatestType.text = "Type: ${alert.type}"
            tvLatestTimestamp.text = "Time: ${alert.getFormattedTimestamp()}"
            tvLatestSequence.text = "Sequence: ${alert.sequenceId}"

            // Location
            alert.location?.let { loc ->
                tvLatestLocation.text = "Location: ${loc.lat}, ${loc.lng} (±${loc.accuracyMeters}m)"
                tvLatestLocation.visibility = View.VISIBLE
            } ?: run {
                tvLatestLocation.visibility = View.GONE
            }

            // Battery
            alert.battery?.let { bat ->
                val chargingStatus = if (bat.isCharging) "Charging" else "Not charging"
                tvLatestBattery.text = "Battery: ${bat.percent}% ($chargingStatus)"
                tvLatestBattery.visibility = View.VISIBLE
            } ?: run {
                tvLatestBattery.visibility = View.GONE
            }
        }
    }

    private fun displayAlertHistory(history: List<Alert>) {
        if (history.isEmpty()) {
            rvAlertHistory.visibility = View.GONE
            tvNoHistory.visibility = View.VISIBLE
        } else {
            rvAlertHistory.visibility = View.VISIBLE
            tvNoHistory.visibility = View.GONE
            historyAdapter.submitList(history)
        }
    }
}
