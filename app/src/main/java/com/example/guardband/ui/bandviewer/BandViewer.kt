package com.example.guardband.ui.bandviewer

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.example.guardband.R

/**
 * A view of the GuardBand band the user can turn. **The Device tab depends on
 * this and nothing else**: it does not know whether frames, a still image or
 * (later) a real-time 3D model are behind it. A `.glb` viewer would implement
 * this interface and be returned from [create]; the tab would not change.
 *
 * Purely visual: no data, no repository, no network, and nothing is stored.
 */
interface BandViewer {

    /** The view to put on screen. */
    val view: View

    /**
     * Whether the viewer is on screen and the app in the foreground. Only
     * while active may it animate or hold decoded images; inactive, it stops
     * every callback and lets its images go. The host calls this from its
     * own visibility (tab shown or hidden, resumed or paused).
     */
    fun setActive(active: Boolean)

    /** Frees everything for good. Call when the view is destroyed. */
    fun release()

    companion object {
        /**
         * The viewer for this build: a turntable of the frames in
         * [TurntableFrames.FOLDER], which with fewer than two frames is a
         * still image, and with none the vector placeholder
         * (`R.drawable.band_placeholder`).
         */
        fun create(context: Context): BandViewer = TurntableBandView(
            context,
            AssetFrameSource(context.assets),
            checkNotNull(ContextCompat.getDrawable(context, R.drawable.band_placeholder))
        )
    }
}
