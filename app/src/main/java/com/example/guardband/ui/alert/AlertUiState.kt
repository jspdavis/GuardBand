package com.example.guardband.ui.alert

import com.example.guardband.data.model.Alert

/** Render state for [AlertFragment]. */
data class AlertUiState(
    /** True until the first read of latest + history completes. */
    val isLoading: Boolean = false,
    /** Ongoing status: the band's most recent alert, or null if it has sent none. */
    val latest: Alert? = null,
    /** Incident history (FR-08), newest first. */
    val history: List<Alert> = emptyList()
)
