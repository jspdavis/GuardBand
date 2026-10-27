package com.example.guardband.ui.avatar

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import coil.dispose
import coil.load
import coil.transform.CircleCropTransformation
import com.example.guardband.R
import com.google.android.material.imageview.ShapeableImageView

/**
 * Binds a circular avatar: the Google profile photo when [show] is given a
 * link (already vetted by [AvatarPhoto]), else the initials it is given, else
 * the default icon. The Device tab's header uses it (the Profile tab's until
 * 2026-10-27). Create one per view in `onViewCreated`, after the view is found.
 *
 * **Initials** are [initialsView], a circle laid over the image view, the
 * same letters the band's marker draws
 * ([BandMarkerFace][com.example.guardband.ui.track.BandMarkerFace]). They show
 * while a photo loads and when it fails, so an email account and an offline
 * Google one read the same as on the map, and go only once a photo arrives.
 *
 * **Nothing square may ever show.** The layout gives the icon a square
 * `surface_card` background and relies on [ShapeableImageView]'s mask to
 * round it. That mask is a paint over a hardware layer, and on a new Google
 * account the avatar was seen drawing square for a moment as the photo
 * landed: the background showing through the photo's transparent corners.
 * Two guards, so the circle never depends on that mask alone:
 *  - the view is also **clipped to its outline**, the same circle, which the
 *    renderer applies on every frame regardless of that mask;
 *  - the photo drops the background altogether. Coil's
 *    [CircleCropTransformation] has already made the photo itself round, so
 *    there is nothing left for the background to do.
 *
 * The icon's tint, inset and background are read from the layout so a photo
 * can drop them and the icon can get them back without repeating the XML.
 * They are dropped only once the photo has actually arrived.
 */
internal class AvatarImage(
    private val view: ShapeableImageView,
    private val initialsView: TextView? = null
) {

    private val iconTint: ColorStateList? = view.imageTintList
    private val iconBackground: Drawable? = view.background
    private val iconPadding = intArrayOf(
        view.contentPaddingLeft, view.contentPaddingTop,
        view.contentPaddingRight, view.contentPaddingBottom
    )

    init {
        // Set here rather than in the layout: android:clipToOutline is API 31.
        view.clipToOutline = true
    }

    /**
     * The default icon goes back **first**, before any request, so a view that
     * was re-created or reused can never keep showing an earlier session's
     * photo while the new one loads. It is also the placeholder and the error
     * drawable, so the avatar is never blank: loading, offline, or a link that
     * has expired all show the icon.
     *
     * A null [url] makes no request at all. Coil cancels the previous request
     * on the view when a new one starts, and [dispose] covers the rest.
     *
     * [initials] cover the icon until a photo arrives; blank (or no
     * [initialsView]) leaves the icon showing.
     */
    fun show(url: String?, initials: String = "") {
        view.dispose()
        initialsView?.text = initials
        showIconStyle()
        view.setImageResource(R.drawable.ic_profile)
        if (url == null) return

        view.load(url) {
            placeholder(R.drawable.ic_profile)
            error(R.drawable.ic_profile)
            transformations(CircleCropTransformation())
            listener(onSuccess = { _, _ -> showPhotoStyle() })
        }
    }

    /** Cancels any request in flight. Call from `onDestroyView`. */
    fun dispose() = view.dispose()

    private fun showIconStyle() {
        view.imageTintList = iconTint
        view.background = iconBackground
        val (left, top, right, bottom) = iconPadding
        view.setContentPadding(left, top, right, bottom)
        initialsView?.let { it.visibility = if (it.text.isNullOrBlank()) View.GONE else View.VISIBLE }
    }

    private fun showPhotoStyle() {
        initialsView?.visibility = View.GONE
        view.imageTintList = null
        view.background = null
        view.setContentPadding(0, 0, 0, 0)
    }
}
