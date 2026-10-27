package com.example.guardband.ui.track

/** One-shot events emitted by [TrackViewModel]. */
sealed interface TrackEvent {

    /**
     * Toast text. The wording stays in [TrackViewModel] as an `MSG_*`
     * constant, which it could not do if the Fragment had to supply its own
     * string for a bare event type.
     */
    data class ShowMessage(val text: String) : TrackEvent
}
