package com.example.guardband.ui.track

import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView

/**
 * Binds the Track map's credits: the full "© MapTiler © OpenStreetMap
 * contributors" line, each part a link, which collapses to a small ⓘ button
 * that brings it back.
 *
 * **When it collapses is the licence's rule, not a style choice.** The
 * OpenStreetMap Foundation's attribution guidelines let a credit collapse
 * automatically after five seconds or after the user interacts with the map
 * (panning, zooming), provided an "(i)" button still leads to it. So it opens
 * expanded every time the Track view is created ([expand]), collapses after
 * [ON_OPEN_MS] or at the first pan ([collapse], from the Fragment's touch
 * listener), and the ⓘ re-opens it for [REOPENED_MS]. MapTiler's own guide
 * only says the text must appear on every map; see `docs/osmdroid.md` §4.6.
 *
 * The collapsed line is INVISIBLE, not GONE, so the check-in pill stacked on
 * it does not move.
 *
 * View ids: track_map_attribution, track_map_attribution_info.
 */
class MapAttribution(
    private val credits: TextView,
    private val info: View,
) {

    private val collapseLater = Runnable { collapse() }

    private var expanded = false

    init {
        credits.text = MapTilerTileSource.attribution()
        credits.movementMethod = LinkMovementMethod.getInstance()
        info.setOnClickListener { expand(REOPENED_MS) }
    }

    /** Shows the full credits, collapsing again after [forMs] or the first pan. */
    fun expand(forMs: Long = ON_OPEN_MS) {
        credits.removeCallbacks(collapseLater)
        expanded = true
        credits.visibility = View.VISIBLE
        info.visibility = View.GONE
        credits.postDelayed(collapseLater, forMs)
    }

    /** Back to the ⓘ. Cheap to call on every touch move. */
    fun collapse() {
        if (!expanded) return
        expanded = false
        credits.removeCallbacks(collapseLater)
        credits.visibility = View.INVISIBLE
        info.visibility = View.VISIBLE
    }

    /** From `onDestroyView`, so a pending collapse does not outlive the view. */
    fun release() {
        credits.removeCallbacks(collapseLater)
    }

    private companion object {
        /** The OSMF guidelines' five seconds. */
        const val ON_OPEN_MS = 5_000L

        /** Longer once asked for, so there is time to tap a link. */
        const val REOPENED_MS = 10_000L
    }
}
