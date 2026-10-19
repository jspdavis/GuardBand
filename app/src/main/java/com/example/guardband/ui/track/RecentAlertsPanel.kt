package com.example.guardband.ui.track

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.ui.alert.AlertHistoryAdapter
import com.example.guardband.ui.alert.AlertUiState

/**
 * Binds the Track tab's recent-alerts sheet (`view_track_recent_alerts.xml`),
 * an include with a binder like [com.example.guardband.ui.auth.PasswordCriteriaList].
 * A class rather than an object because it holds its adapter.
 *
 * It renders the host-scoped [AlertUiState] - [AlertUiState.recent] for the
 * rows, and the same loading, error and empty fields the Alert history screen
 * shows - so the sheet and the history can never disagree. Rows are
 * [AlertHistoryAdapter]'s `item_alert.xml`, unchanged. Tapping a row does
 * nothing, as it did on the old Alert tab.
 *
 * **Dragging is not this class's job.** The sheet is a `BottomSheetBehavior`
 * child of `fragment_track.xml`'s CoordinatorLayout: the behavior handles the
 * drag, the drag handle expands and collapses it for a tap or TalkBack, and
 * the CoordinatorLayout saves which state it was in.
 *
 * View ids: track_recent_alerts_drag_handle, ll_track_recent_alerts_header,
 * tv_track_recent_alerts_title, btn_track_recent_alerts_see_all,
 * ll_track_recent_alerts_body, track_recent_alerts_progress,
 * track_recent_alerts_error_view, track_recent_alerts_error_message,
 * track_recent_alerts_retry_button, track_recent_alerts_empty_view,
 * track_recent_alerts_list.
 */
class RecentAlertsPanel(
    root: View,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit
) {

    private val progress: View = root.findViewById(R.id.track_recent_alerts_progress)
    private val errorView: View = root.findViewById(R.id.track_recent_alerts_error_view)
    private val tvError: TextView = root.findViewById(R.id.track_recent_alerts_error_message)
    private val tvEmpty: TextView = root.findViewById(R.id.track_recent_alerts_empty_view)

    private val adapter = AlertHistoryAdapter()

    init {
        root.findViewById<RecyclerView>(R.id.track_recent_alerts_list).adapter = adapter
        root.findViewById<View>(R.id.btn_track_recent_alerts_see_all)
            .setOnClickListener { onSeeAll() }
        root.findViewById<View>(R.id.track_recent_alerts_retry_button)
            .setOnClickListener { onRetry() }
    }

    fun render(state: AlertUiState) {
        progress.isVisible = state.isLoading

        val error = state.errorMessage
        errorView.isVisible = error != null
        if (error != null) tvError.text = error

        val empty = state.emptyMessage
        tvEmpty.isVisible = empty != null
        if (empty != null) tvEmpty.text = empty

        adapter.submitList(state.rowsOf(state.recent))
    }
}
