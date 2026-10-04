package com.example.guardband.ui.track

/** One-shot events emitted by [TrackViewModel]. */
sealed interface TrackEvent {
    data class ShowMessage(val text: String) : TrackEvent
}
