package com.example.guardband.ui.alert

import android.os.Bundle
import android.view.View
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
import kotlinx.coroutines.launch

/**
 * Alert tab: the band's current status (latest alert card) above its
 * incident history. Data is live from [AlertViewModel].
 */
class AlertFragment : Fragment(R.layout.fragment_alert) {

    private lateinit var progressBar: ProgressBar
    private lateinit var latestCard: View
    private lateinit var latestHolder: AlertHistoryAdapter.AlertViewHolder
    private lateinit var tvNone: TextView
    private lateinit var tvHistoryEmpty: TextView
    private val historyAdapter = AlertHistoryAdapter()

    private val viewModel: AlertViewModel by viewModels { AlertViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar    = view.findViewById(R.id.progress_alert)
        latestCard     = view.findViewById(R.id.include_alert_latest)
        latestHolder   = AlertHistoryAdapter.AlertViewHolder(latestCard)
        tvNone         = view.findViewById(R.id.tv_alert_none)
        tvHistoryEmpty = view.findViewById(R.id.tv_alert_history_empty)
        view.findViewById<RecyclerView>(R.id.rv_alert_history).adapter = historyAdapter

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

        val latest = state.latest
        latestCard.visibility = if (latest == null) View.GONE else View.VISIBLE
        if (latest != null) latestHolder.bind(latest)
        tvNone.visibility = if (!state.isLoading && latest == null) View.VISIBLE else View.GONE

        tvHistoryEmpty.visibility =
            if (!state.isLoading && state.history.isEmpty()) View.VISIBLE else View.GONE
        historyAdapter.submitList(state.history)
    }

    private fun handleEvent(event: AlertEvent) {
        when (event) {
            is AlertEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
        }
    }
}
