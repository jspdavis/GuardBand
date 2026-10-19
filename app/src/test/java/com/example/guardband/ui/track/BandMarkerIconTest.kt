package com.example.guardband.ui.track

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The band marker is drawn when its face changes and not otherwise, and the
 * anchor is the pointer's tip. Faces without a photo only, so nothing here
 * reaches the network or the image loader; the photo path needs a device.
 */
@RunWith(RobolectricTestRunner::class)
class BandMarkerIconTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
    }

    private val drawn = mutableListOf<Drawable>()

    private fun icon() = BandMarkerIcon(RuntimeEnvironment.getApplication(), owner) { drawn += it }

    @Test
    fun `a face is drawn at once, before any photo could load`() {
        icon().bind(BandMarkerFace(photoUrl = null, initials = "AR"))

        assertEquals(1, drawn.size)
        assertTrue(drawn.single() is BitmapDrawable)
    }

    @Test
    fun `the same face again draws nothing`() {
        val icon = icon()

        icon.bind(BandMarkerFace(photoUrl = null, initials = "AR"))
        icon.bind(BandMarkerFace(photoUrl = null, initials = "AR"))
        icon.bind(BandMarkerFace(photoUrl = null, initials = "AR"))

        assertEquals(1, drawn.size)
    }

    @Test
    fun `a different face draws again`() {
        val icon = icon()

        icon.bind(BandMarkerFace(photoUrl = null, initials = "AR"))
        icon.bind(BandMarkerFace(photoUrl = null, initials = "JC"))

        assertEquals(2, drawn.size)
    }

    @Test
    fun `after release the same face is drawn again for a new view`() {
        val icon = icon()

        icon.bind(BandMarkerFace(photoUrl = null, initials = "AR"))
        icon.release()
        icon.bind(BandMarkerFace(photoUrl = null, initials = "AR"))

        assertEquals(2, drawn.size)
    }

    @Test
    fun `a blank face still draws a marker`() {
        icon().bind(BandMarkerFace(photoUrl = null, initials = ""))

        assertEquals(1, drawn.size)
    }

    @Test
    fun `the anchor is the pointer's tip, centred and near the bottom`() {
        val icon = icon()
        icon.bind(BandMarkerFace(photoUrl = null, initials = "AR"))
        val bitmap = (drawn.single() as BitmapDrawable).bitmap

        // 58 x 66 dp, tip 62 dp down at the centre.
        assertEquals(0.5f, icon.anchorU, 0.01f)
        assertEquals(62f / 66f, icon.anchorV, 0.02f)
        assertEquals(58f / 66f, bitmap.width.toFloat() / bitmap.height, 0.02f)
    }
}
