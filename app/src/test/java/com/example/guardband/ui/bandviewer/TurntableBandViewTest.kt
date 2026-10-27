package com.example.guardband.ui.bandviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.guardband.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The viewer as the Device tab uses it: today's build (no frames, so the
 * placeholder), a single image, a full turntable, broken frames, the
 * scroll-friendly touch handling and the TalkBack actions. Nothing here may
 * throw: a still image ignores every touch, and a frame that fails to load
 * falls back to the placeholder.
 */
@RunWith(RobolectricTestRunner::class)
class TurntableBandViewTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private class FakeSource(override val count: Int, private val broken: Boolean = false) : FrameSource {
        override fun decode(index: Int): Bitmap? =
            if (broken) null else Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
    }

    /** Records whether its child asked it not to intercept, like the Device tab's ScrollView. */
    private class RecordingParent(context: Context) : FrameLayout(context) {
        var disallowed = false
        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            if (disallowIntercept) disallowed = true
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private fun viewer(frames: Int, broken: Boolean = false) = TurntableBandView(
        context,
        FakeSource(frames, broken),
        checkNotNull(ContextCompat.getDrawable(context, R.drawable.band_placeholder))
    )

    /** Lays [view] out at 400 × 300 px inside a [RecordingParent], and draws it once. */
    private fun laidOut(view: View): RecordingParent {
        val parent = RecordingParent(context)
        parent.addView(view, FrameLayout.LayoutParams(400, 300))
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, 400, 300)
        draw(view)
        return parent
    }

    private fun draw(view: View) = view.draw(Canvas(Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)))

    private fun touch(view: View, action: Int, x: Float, y: Float, time: Long): Boolean {
        val event = MotionEvent.obtain(0L, time, action, x, y, 0)
        return try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** A horizontal drag from x = 100 to [toX], ending without speed. */
    private fun dragTo(view: View, toX: Float) {
        touch(view, MotionEvent.ACTION_DOWN, 100f, 150f, 0)
        touch(view, MotionEvent.ACTION_MOVE, 130f, 150f, 10) // past the slop: the drag starts here
        touch(view, MotionEvent.ACTION_MOVE, toX, 150f, 20)
        touch(view, MotionEvent.ACTION_UP, toX, 150f, 1_000)
    }

    private fun actionLabels(view: View): List<CharSequence?> {
        val info = AccessibilityNodeInfoCompat.obtain()
        ViewCompat.onInitializeAccessibilityNodeInfo(view, info)
        return info.actionList.map { it.label }
    }

    private fun performAction(view: View, label: String) {
        val info = AccessibilityNodeInfoCompat.obtain()
        ViewCompat.onInitializeAccessibilityNodeInfo(view, info)
        val action = info.actionList.first { it.label == label }
        ViewCompat.performAccessibilityAction(view, action.id, null)
    }

    // ── Today's build: the placeholder ────────────────────────────────────────

    @Test
    fun `the Device tab's viewer, with no frames in the app, is the still placeholder`() {
        val bandViewer = BandViewer.create(context)
        val view = bandViewer.view as TurntableBandView
        laidOut(view)

        bandViewer.setActive(true)
        draw(view)
        dragTo(view, 300f)
        draw(view)
        bandViewer.setActive(false)
        bandViewer.release()

        assertFalse(view.canRotate)
        assertEquals(0, view.currentFrame)
        assertEquals(context.getString(R.string.cd_band_viewer_still), view.contentDescription)
    }

    // ── A single image ────────────────────────────────────────────────────────

    @Test
    fun `a single frame shows still, ignores touches and never claims the scroll`() {
        val view = viewer(frames = 1)
        val parent = laidOut(view)
        view.setActive(true)

        val claimed = touch(view, MotionEvent.ACTION_DOWN, 100f, 150f, 0)
        touch(view, MotionEvent.ACTION_MOVE, 300f, 150f, 10)
        touch(view, MotionEvent.ACTION_UP, 300f, 150f, 20)
        draw(view)

        assertFalse(claimed)
        assertFalse(parent.disallowed)
        assertEquals(0, view.currentFrame)
        assertTrue(actionLabels(view).none { it == context.getString(R.string.action_band_viewer_next) })
        view.release()
    }

    @Test
    fun `no frames at all draws the placeholder without a crash`() {
        val view = viewer(frames = 0)
        laidOut(view)
        view.setActive(true)

        draw(view)
        dragTo(view, 300f)

        assertEquals(0, view.currentFrame)
        view.release()
    }

    // ── A full turntable ──────────────────────────────────────────────────────

    @Test
    fun `a horizontal drag turns it and takes the gesture from the scroll`() {
        val view = viewer(frames = 36)
        val parent = laidOut(view) // 400 px wide: a turn is 600 px, a frame 16.7 px

        dragTo(view, 180f) // 50 px after the slop

        assertTrue(view.canRotate)
        assertEquals(3, view.currentFrame)
        assertTrue(parent.disallowed)
        assertEquals(context.getString(R.string.cd_band_viewer), view.contentDescription)
        view.release()
    }

    @Test
    fun `dragging left from the first frame wraps to the end`() {
        val view = viewer(frames = 36)
        laidOut(view)

        dragTo(view, 80f) // 50 px left after the slop

        assertEquals(33, view.currentFrame)
        view.release()
    }

    @Test
    fun `a vertical swipe on the band is left to the scroll`() {
        val view = viewer(frames = 36)
        val parent = laidOut(view)

        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        touch(view, MotionEvent.ACTION_MOVE, 104f, 160f, 10)
        touch(view, MotionEvent.ACTION_CANCEL, 104f, 160f, 20) // the ScrollView intercepted

        assertFalse(parent.disallowed)
        assertEquals(0, view.currentFrame)
        view.release()
    }

    @Test
    fun `TalkBack can turn it with Next angle and Previous angle`() {
        val view = viewer(frames = 72)
        laidOut(view)
        val next = context.getString(R.string.action_band_viewer_next)
        val previous = context.getString(R.string.action_band_viewer_previous)

        assertTrue(actionLabels(view).containsAll(listOf(next, previous)))
        performAction(view, next)
        assertEquals(9, view.currentFrame) // an eighth of a turn
        performAction(view, previous)
        performAction(view, previous)
        assertEquals(63, view.currentFrame)
        view.release()
    }

    // ── Failures and lifecycle ────────────────────────────────────────────────

    @Test
    fun `frames that fail to load fall back to the placeholder without a crash`() {
        val view = viewer(frames = 36, broken = true)
        laidOut(view)
        view.setActive(true)

        repeat(3) { draw(view) }
        dragTo(view, 180f)
        draw(view)

        assertEquals(3, view.currentFrame)
        view.release()
    }

    @Test
    fun `it can be activated, paused, released and detached in any order`() {
        val view = viewer(frames = 36)
        val parent = laidOut(view)

        view.setActive(true)
        view.setActive(true)
        view.setActive(false)
        view.release()
        view.setActive(true)
        draw(view)
        parent.removeView(view)
        view.release()

        assertNull(view.parent)
    }
}
