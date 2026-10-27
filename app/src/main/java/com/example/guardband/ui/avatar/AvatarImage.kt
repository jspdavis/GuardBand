package com.example.guardband.ui.avatar

import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import coil.dispose
import coil.load
import coil.transform.CircleCropTransformation
import com.example.guardband.R

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
 * **Nothing square may ever show, and nothing here relies on a mask.** The
 * avatar used to be a Material `ShapeableImageView`, which rounds itself by
 * painting over its corners (`DST_OUT`) inside a hardware layer it switches
 * on when attached and off when detached. When that layer was not in place
 * the paint punched through to the window instead, and the corners showed
 * square: on the Profile tab as a new Google account's photo landed, and on
 * the Device tab after a switch to dark mode, which recreates the screen
 * (2026-10-28). **Do not go back to `ShapeableImageView` for this.** Now:
 *  - [view] is a plain `ImageView`, **clipped to a circle outline**
 *    ([CircleOutline]). The renderer applies that clip on every frame, with
 *    no layer and no paint trick, so it survives a recreate;
 *  - the icon's background in the layout is itself a circle
 *    (`@drawable/bg_avatar_icon`), never a plain colour;
 *  - the photo is round already (Coil's [CircleCropTransformation]) and drops
 *    the background.
 *
 * The icon's tint, padding and background are read from the layout so a
 * photo can drop them and the icon can get them back without repeating the
 * XML. They are dropped only once the photo has actually arrived.
 */
internal class AvatarImage(
    private val view: ImageView,
    private val initialsView: TextView? = null
) {

    private val iconTint: ColorStateList? = view.imageTintList
    private val iconBackground: Drawable? = view.background
    private val iconPadding = intArrayOf(
        view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom
    )

    init {
        // In code rather than the layout: android:clipToOutline is API 31.
        view.outlineProvider = CircleOutline
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
        view.setPadding(left, top, right, bottom)
        initialsView?.let { it.visibility = if (it.text.isNullOrBlank()) View.GONE else View.VISIBLE }
    }

    private fun showPhotoStyle() {
        initialsView?.visibility = View.GONE
        view.imageTintList = null
        view.background = null
        view.setPadding(0, 0, 0, 0)
    }

    /**
     * A circle filling the view, whatever its background: the outline the
     * avatar is clipped to. Not the background's own outline, which a photo
     * removes.
     */
    private object CircleOutline : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}
