package com.example.guardband.ui.alert

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import kotlinx.coroutines.launch

/**
 * Alert tab: the band's current status (latest card) above its incident
 * history. Data is live from [AlertViewModel].
 *
 * View ids: alert_progress, alert_error_view, alert_error_message,
 * alert_retry_button, alert_latest_card, alert_latest_dot, alert_latest_type,
 * alert_latest_time, alert_latest_location, alert_latest_battery,
 * alert_empty_view, alert_history_list, tv_alert_title,
 * tv_alert_latest_label, tv_alert_history_label.
 *
 * The latest card is its own block rather than an `<include>` of
 * `item_alert.xml`: an include cannot rename its children, so sharing the
 * layout would have meant the card and a history row answering to the same
 * ids.
 */
class AlertFragment : Fragment(R.layout.fragment_alert) {

    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    private lateinit var latestCard: View
    private lateinit var latestDot: View
    private lateinit var tvLatestType: TextView
    private lateinit var tvLatestTime: TextView
    private lateinit var tvLatestLocation: TextView
    private lateinit var tvLatestBattery: TextView
    private lateinit var tvEmpty: TextView

    private val historyAdapter = AlertHistoryAdapter()

    private val viewModel: AlertViewModel by viewModels { AlertViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar       = view.findViewById(R.id.alert_progress)
        errorView         = view.findViewById(R.id.alert_error_view)
        tvErrorMessage    = view.findViewById(R.id.alert_error_message)
        btnRetry          = view.findViewById(R.id.alert_retry_button)
        latestCard        = view.findViewById(R.id.alert_latest_card)
        latestDot         = view.findViewById(R.id.alert_latest_dot)
        tvLatestType      = view.findViewById(R.id.alert_latest_type)
        tvLatestTime      = view.findViewById(R.id.alert_latest_time)
        tvLatestLocation  = view.findViewById(R.id.alert_latest_location)
        tvLatestBattery   = view.findViewById(R.id.alert_latest_battery)
        tvEmpty           = view.findViewById(R.id.alert_empty_view)

        view.findViewById<RecyclerView>(R.id.alert_history_list).adapter = historyAdapter

        btnRetry.setOnClickListener { viewModel.onRetryClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun render(state: AlertUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        val error = state.errorMessage
        errorView.visibility = if (error == null) View.GONE else View.VISIBLE
        if (error != null) tvErrorMessage.text = error

        val latest = state.latest
        latestCard.visibility = if (latest == null) View.GONE else View.VISIBLE
        if (latest != null) bindLatest(latest)

        val empty = state.emptyMessage
        tvEmpty.visibility = if (empty == null) View.GONE else View.VISIBLE
        if (empty != null) tvEmpty.text = empty

        historyAdapter.submitList(state.history)
    }

    private fun bindLatest(alert: Alert) {
        val type = alert.alertType()

        tvLatestType.setText(AlertFormatting.typeLabel(type))
        latestDot.backgroundTintList =
            ColorStateList.valueOf(requireContext().getColor(AlertFormatting.typeColor(type)))
        tvLatestTime.text = AlertFormatting.formatTimestamp(alert.timestamp)

        val location = alert.location
        tvLatestLocation.visibility = if (location == null) View.GONE else View.VISIBLE
        if (location != null) {
            tvLatestLocation.text =
                getString(R.string.label_alert_location, location.lat, location.lng)
        }

        val battery = alert.battery
        tvLatestBattery.visibility = if (battery == null) View.GONE else View.VISIBLE
        if (battery != null) {
            tvLatestBattery.text = getString(
                if (battery.isCharging) R.string.label_alert_battery_charging
                else R.string.label_alert_battery,
                battery.percent
            )
        }
    }

    private fun handleEvent(event: AlertEvent) {
        when (event) {
            is AlertEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
        }
    }
}
