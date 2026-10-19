package com.example.guardband.ui.track

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.guardband.R
import com.example.guardband.ui.alert.AlertHistoryAdapter
import com.example.guardband.ui.alert.AlertUiState

/**
 * Binds the Track tab's recent-alerts panel (`view_track_recent_alerts.xml`),
 * an include with a binder like [com.example.guardband.ui.auth.PasswordCriteriaList].
 * A class rather than an object because it holds its adapter and whether it
 * is expanded; it has no other state.
 *
 * It renders the host-scoped [AlertUiState] - [AlertUiState.recent] for the
 * rows, and the same loading, error and empty fields the Alert history screen
 * shows - so the panel and the history can never disagree. Rows are
 * [AlertHistoryAdapter]'s `item_alert.xml`, unchanged. Tapping a row does
 * nothing, as it did on the old Alert tab.
 *
 * Whether it is expanded is view state, like the map's camera, so it lives
 * here and in the Fragment's saved state, not in a ViewModel.
 *
 * View ids: ll_track_recent_alerts_header, iv_track_recent_alerts_toggle,
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

    private val header: View = root.findViewById(R.id.ll_track_recent_alerts_header)
    private val toggle: ImageView = root.findViewById(R.id.iv_track_recent_alerts_toggle)
    private val body: View = root.findViewById(R.id.ll_track_recent_alerts_body)
    private val progress: View = root.findViewById(R.id.track_recent_alerts_progress)
    private val errorView: View = root.findViewById(R.id.track_recent_alerts_error_view)
    private val tvError: TextView = root.findViewById(R.id.track_recent_alerts_error_message)
    private val tvEmpty: TextView = root.findViewById(R.id.track_recent_alerts_empty_view)

    private val adapter = AlertHistoryAdapter()

    /** True while the rows are showing; collapsing leaves only the header. */
    var isExpanded: Boolean = true
        private set

    init {
        root.findViewById<RecyclerView>(R.id.track_recent_alerts_list).adapter = adapter
        header.setOnClickListener { setExpanded(!isExpanded) }
        root.findViewById<View>(R.id.btn_track_recent_alerts_see_all)
            .setOnClickListener { onSeeAll() }
        root.findViewById<View>(R.id.track_recent_alerts_retry_button)
            .setOnClickListener { onRetry() }
        setExpanded(true)
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        body.isVisible = expanded
        toggle.rotation = if (expanded) ROTATION_EXPANDED else ROTATION_COLLAPSED
        // TalkBack: "double-tap to collapse recent alerts", not "to activate".
        ViewCompat.replaceAccessibilityAction(
            header, AccessibilityActionCompat.ACTION_CLICK,
            header.context.getString(
                if (expanded) R.string.action_track_recent_alerts_collapse
                else R.string.action_track_recent_alerts_expand
            ),
            null
        )
    }

    fun render(state: AlertUiState) {
        progress.isVisible = state.isLoading

        val error = state.errorMessage
        errorView.isVisible = error != null
        if (error != null) tvError.text = error

        val empty = state.emptyMessage
        tvEmpty.isVisible = empty != null
        if (empty != null) tvEmpty.text = empty

        adapter.submitList(state.recent)
    }

    private companion object {
        /** `ic_chevron_right` turned to point down (open) or up (closed). */
        const val ROTATION_EXPANDED = 90f
        const val ROTATION_COLLAPSED = -90f
    }
}
