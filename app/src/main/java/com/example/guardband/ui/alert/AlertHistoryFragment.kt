package com.example.guardband.ui.alert

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * Alert history: the band's current status (latest card) above its whole
 * incident history (FR-08). Data is live from [AlertViewModel].
 *
 * A **pushed screen** in the Home host, not a tab: "See all" on Track's
 * recent-alerts panel opens it, the host hides the bottom bar while it shows,
 * and Back (or the back arrow) returns to Track. Being inside the host puts
 * it behind `HomeViewModel`'s gates like the tabs. It was the Alert tab until
 * 2026-10-19, and is that tab's screen with a back arrow in place of the gear
 * and bell.
 *
 * Its [AlertViewModel] is the host-scoped one the Track panel also reads, so
 * opening this screen starts no second read: the list is already loaded.
 *
 * View ids: ib_alert_history_back, tv_alert_history_title, alert_progress,
 * alert_error_view, alert_error_message, alert_retry_button,
 * alert_latest_card, alert_latest_icon, alert_latest_type, alert_latest_time,
 * alert_latest_place_row, alert_latest_location, alert_latest_battery_row,
 * alert_latest_battery_icon, alert_latest_battery, alert_empty_view,
 * alert_history_list, tv_alert_latest_label, tv_alert_history_label.
 *
 * The latest card is its own block rather than an `<include>` of
 * `item_alert.xml`: an include cannot rename its children, so sharing the
 * layout would have meant the card and a history row answering to the same
 * ids. It is the same shape, though, and [AlertCardViews] binds both. The
 * card says "5 min ago"; the history's rows sit under a heading for each day
 * and show the clock time.
 */
class AlertHistoryFragment : Fragment(R.layout.fragment_alert_history) {

    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    private lateinit var latestCard: MaterialCardView
    private lateinit var latestViews: AlertCardViews
    private lateinit var tvEmpty: TextView

    private val historyAdapter = AlertHistoryAdapter(AlertTimeStyle.CLOCK)

    // Host-scoped: the one instance the Track tab shares. See AlertViewModel.
    private val viewModel: AlertViewModel by activityViewModels { AlertViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_alert_history_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        progressBar       = view.findViewById(R.id.alert_progress)
        errorView         = view.findViewById(R.id.alert_error_view)
        tvErrorMessage    = view.findViewById(R.id.alert_error_message)
        btnRetry          = view.findViewById(R.id.alert_retry_button)
        latestCard        = view.findViewById(R.id.alert_latest_card)
        tvEmpty           = view.findViewById(R.id.alert_empty_view)
        latestViews = AlertCardViews(
            card = latestCard,
            icon = view.findViewById(R.id.alert_latest_icon),
            type = view.findViewById(R.id.alert_latest_type),
            time = view.findViewById(R.id.alert_latest_time),
            placeRow = view.findViewById(R.id.alert_latest_place_row),
            place = view.findViewById(R.id.alert_latest_location),
            batteryRow = view.findViewById(R.id.alert_latest_battery_row),
            batteryIcon = view.findViewById(R.id.alert_latest_battery_icon),
            battery = view.findViewById(R.id.alert_latest_battery)
        )

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
        if (latest != null) {
            latestViews.bind(state.rowsOf(listOf(latest)).single(), AlertTimeStyle.RELATIVE)
        }

        val empty = state.emptyMessage
        tvEmpty.visibility = if (empty == null) View.GONE else View.VISIBLE
        if (empty != null) tvEmpty.text = empty

        historyAdapter.submitList(state.historyItems())
    }

    private fun handleEvent(event: AlertEvent) {
        when (event) {
            is AlertEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
        }
    }
}
