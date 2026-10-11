package com.example.guardband.ui.alert

/** One-shot events emitted by [AlertViewModel]. */
sealed interface AlertEvent {
    data class ShowMessage(val text: String) : AlertEvent
}
