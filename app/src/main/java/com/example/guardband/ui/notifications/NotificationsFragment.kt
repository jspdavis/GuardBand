package com.example.guardband.ui.notifications

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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
import com.example.guardband.ui.alert.AlertHistoryAdapter
import com.example.guardband.ui.alert.AlertTimeStyle
import com.example.guardband.ui.alert.AlertUiState
import kotlinx.coroutines.launch

/**
 * Notifications inbox: the band's non-tracking alert history, newest first,
 * grouped under day headings. Pushed from the Track tab's bell icon; the
 * Home host hides the bottom bar while it is visible.
 *
 * Uses its own fragment-scoped [NotificationsViewModel] so the subscription
 * is released the moment the user navigates back, rather than persisting
 * for the life of the Home host. The inbox and the Alert history screen are
 * never open simultaneously, so the extra listener is fine.
 *
 * Reuses [AlertHistoryAdapter] (rows + day headers) and `item_alert.xml`
 * unchanged. Times are shown relative ("5 min ago") because inbox rows are
 * not grouped under a day heading that supplies the date context.
 *
 * View ids: ib_notifications_back, tv_notifications_title,
 * notifications_progress, notifications_error_view,
 * notifications_error_message, notifications_retry_button,
 * notifications_list, tv_notifications_empty.
 */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView

    private val viewModel: NotificationsViewModel by viewModels { NotificationsViewModel.Factory }

    private val adapter = AlertHistoryAdapter(AlertTimeStyle.RELATIVE)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_notifications_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        progressBar    = view.findViewById(R.id.notifications_progress)
        errorView      = view.findViewById(R.id.notifications_error_view)
        tvErrorMessage = view.findViewById(R.id.notifications_error_message)
        btnRetry       = view.findViewById(R.id.notifications_retry_button)
        recyclerView   = view.findViewById(R.id.notifications_list)
        tvEmpty        = view.findViewById(R.id.tv_notifications_empty)

        recyclerView.adapter = adapter

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
        errorView.visibility = if (error != null) View.VISIBLE else View.GONE
        if (error != null) tvErrorMessage.text = error

        val empty = state.emptyMessage
        tvEmpty.visibility = if (!state.isLoading && error == null && empty != null) View.VISIBLE else View.GONE
        if (empty != null) tvEmpty.text = empty

        val showList = !state.isLoading && error == null && empty == null
        recyclerView.visibility = if (showList) View.VISIBLE else View.GONE

        // historyItems() groups the non-tracking history under day headings.
        adapter.submitList(state.historyItems())
    }

    private fun handleEvent(event: NotificationsEvent) {
        when (event) {
            is NotificationsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
        }
    }
}
