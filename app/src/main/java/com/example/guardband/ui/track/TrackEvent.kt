package com.example.guardband.ui.track

import com.example.guardband.data.model.Alert

/** One-shot events emitted by [TrackViewModel]. */
sealed interface TrackEvent {

    /**
     * Toast text. The wording stays in [TrackViewModel] as an `MSG_*`
     * constant, which it could not do if the Fragment had to supply its own
     * string for a bare event type.
     */
    data class ShowMessage(val text: String) : TrackEvent

    /**
     * Open [panic]'s Incident view: from a panic pin, or from "View incident"
     * on the band's bubble. The Fragment hands it to
     * [HomeViewModel.onOpenIncident][com.example.guardband.ui.home.HomeViewModel.onOpenIncident],
     * the one way every entry point opens an incident.
     */
    data class OpenIncident(val panic: Alert) : TrackEvent
}
