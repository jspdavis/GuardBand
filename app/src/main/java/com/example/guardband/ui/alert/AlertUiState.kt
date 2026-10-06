package com.example.guardband.ui.alert

import com.example.guardband.data.model.Alert

/**
 * Render state for [AlertFragment].
 *
 * Four situations in one class: loading, content, empty and error. They are
 * distinguished by which fields are set rather than by a sealed hierarchy,
 * because the Fragment needs to keep showing the last good list while a
 * refresh fails - a sealed state would have to throw that list away to become
 * an error.
 */
data class AlertUiState(
    /** True until the first read of the history window completes. */
    val isLoading: Boolean = true,

    /**
     * The band's most recent **non-tracking** alert (D2), shown in the latest
     * card. Derived from history, not from `devices/{id}/latest`, which may
     * hold a TRACKING_UPDATE.
     */
    val latest: Alert? = null,

    /**
     * Incident history (FR-08), newest first: tracking updates removed (D1)
     * and capped at [AlertViewModel.MAX_HISTORY] (D3).
     */
    val history: List<Alert> = emptyList(),

    /**
     * Non-null when the last read failed **and** there is nothing on screen to
     * fall back to. Shows the error view and its Retry button.
     *
     * A failure that arrives while content is already displayed does not set
     * this; it goes out as [AlertEvent.ShowMessage] instead, so the user is
     * told the list is stale without losing it.
     */
    val errorMessage: String? = null,

    /**
     * Non-null when the read succeeded but there is nothing to display, and
     * says *why* - the band has sent nothing, the window held only tracking
     * updates, or nothing in it could be read.
     */
    val emptyMessage: String? = null
)
