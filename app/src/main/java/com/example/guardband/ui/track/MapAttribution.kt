package com.example.guardband.ui.track

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.Layout
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginEnd
import androidx.core.view.updateLayoutParams
import kotlin.math.ceil

/**
 * Binds the Track map's credits: the full "© MapTiler © OpenStreetMap
 * contributors" line, each part a link, which collapses to a small ⓘ button
 * that brings it back; and the MapTiler logo, a link to
 * [MapTilerTileSource.LOGO_URL].
 *
 * **The logo never collapses.** MapTiler's Free plan requires it on the map,
 * and nothing here changes its visibility; only the text line goes to the ⓘ.
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
 * The collapsed line is INVISIBLE, not GONE, so it keeps its place and the
 * check-in pill, which stacks on a barrier over it, does not move.
 *
 * **Beside the logo, or above it.** The line sits bottom right in the logo's
 * row when it fits there; on a narrow screen or with a large system font it
 * would run into the logo, so before the first draw it moves onto a line of
 * its own directly above the logo, right-aligned, wrapping if even that line
 * is too narrow ([placeBesideOrAboveLogo]). It is never made smaller.
 *
 * View ids: track_map_attribution, track_map_attribution_info, track_map_logo.
 */
class MapAttribution(
    private val credits: TextView,
    private val info: View,
    private val logo: View,
) {

    private val collapseLater = Runnable { collapse() }

    /** See [placeBesideOrAboveLogo]. Declared before `init`, which registers it. */
    private val placeOnce = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if ((credits.parent as View).width == 0) return true
            credits.viewTreeObserver.removeOnPreDrawListener(this)
            if (fitsBesideLogo()) return true
            stackAboveLogo()
            return false
        }
    }

    private var expanded = false

    init {
        credits.text = MapTilerTileSource.attribution()
        credits.movementMethod = LinkMovementMethod.getInstance()
        info.setOnClickListener { expand(REOPENED_MS) }
        logo.setOnClickListener { openLogoLink(it) }
        placeBesideOrAboveLogo()
    }

    /**
     * Decided once, before the first draw, from the line's real width, so the
     * system font size counts as well as the screen width; a fixed dp
     * threshold could not. Layout alone cannot do it: ConstraintLayout's
     * `Flow` and chains wrap downwards, which would lift the logo off its row,
     * and an upward wrap needs Flexbox, a new dependency. Once is enough: a
     * change of width, density or font size recreates the Activity, and with
     * it this view.
     *
     * Moving the line moves nothing else while it stays one line: the ⓘ
     * anchors to the logo, and the pill to `barrier_track_credits`, whose
     * clearance above the logo already holds one line. Only a line that
     * wraps (the largest font sizes) lifts the pill, and the buttons stacked
     * on it, by its extra height. The draw that would show the line in the
     * wrong place is skipped.
     *
     * **It waits for the row to be laid out.** A restore can rebuild Track
     * while another tab is showing; Track's views are then hidden, have no
     * width, and would always measure as "does not fit". The decision is
     * made on the first draw after Track has a width.
     */
    private fun placeBesideOrAboveLogo() {
        credits.viewTreeObserver.addOnPreDrawListener(placeOnce)
    }

    private fun stackAboveLogo() {
        credits.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToTop = logo.id
            bottomMargin = 0
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            marginStart = credits.marginEnd
            horizontalBias = 1f
            constrainedWidth = true
        }
    }

    /** Whether the whole line, on one line, fits between the logo and the edge. */
    private fun fitsBesideLogo(): Boolean {
        val row = credits.parent as View
        val line = ceil(Layout.getDesiredWidth(credits.text, credits.paint)).toInt() +
            credits.paddingStart + credits.paddingEnd
        val gap = (GAP_DP * credits.resources.displayMetrics.density).toInt()
        return logo.right + gap + line + credits.marginEnd <= row.width
    }

    /**
     * The same plain `ACTION_VIEW` the credits' links send. A phone with no
     * browser gets nothing at all, as a tap on one of those links does.
     */
    private fun openLogoLink(view: View) {
        try {
            view.context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(MapTilerTileSource.LOGO_URL))
            )
        } catch (e: ActivityNotFoundException) {
            // Nothing to open it with; the logo itself is still on the map.
        }
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
        credits.viewTreeObserver.removeOnPreDrawListener(placeOnce)
    }

    private companion object {
        /** The OSMF guidelines' five seconds. */
        const val ON_OPEN_MS = 5_000L

        /** Longer once asked for, so there is time to tap a link. */
        const val REOPENED_MS = 10_000L

        /** The least space between the logo and the line beside it. */
        const val GAP_DP = 8
    }
}
