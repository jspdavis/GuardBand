package com.example.guardband.ui.track

/** One-shot events emitted by [TrackViewModel]. */
sealed interface TrackEvent {

    /**
     * Toast text. Also how "navigation unavailable" reaches the screen: the
     * wording stays in [TrackViewModel] as an `MSG_*` constant, which it could
     * not do if the Fragment had to supply its own string for a bare event
     * type.
     */
    data class ShowMessage(val text: String) : TrackEvent

    /** D6: hand [url] to whatever maps app the device has. */
    data class OpenNavigation(val url: String) : TrackEvent
}
