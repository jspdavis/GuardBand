package com.example.guardband.ui.bandviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The turntable's maths: drag to frame, wrap-around both ways, fling decay,
 * idle auto-rotation, the "remove animations" setting, and the edge cases of
 * 0, 1 and a great many frames. Pure; time is passed in.
 */
class TurntableMotionTest {

    /** 36 frames over a 3600 px turn: 100 px per frame. */
    private fun motion(frames: Int = 36, pixelsPerTurn: Float = 3600f) =
        TurntableMotion(frames, pixelsPerTurn)

    /** Runs [step] every 16 ms from [from] to [until]. */
    private fun TurntableMotion.run(from: Long, until: Long, animations: Boolean = true) {
        var t = from
        while (t <= until) {
            step(t, animations)
            t += 16
        }
    }

    // ── Drag to frame ─────────────────────────────────────────────────────────

    @Test
    fun `dragging right moves forward one frame per frame-width`() {
        val m = motion()
        m.touchDown(0)

        m.drag(100f, 10)
        assertEquals(1, m.frameIndex)

        m.drag(250f, 20)
        assertEquals(4, m.frameIndex) // 3.5 frames rounds to the nearest
    }

    @Test
    fun `dragging left moves backward`() {
        val m = motion()
        m.touchDown(0)
        m.drag(300f, 10)

        m.drag(-200f, 20)

        assertEquals(1, m.frameIndex)
    }

    @Test
    fun `a drag below half a frame stays on the frame`() {
        val m = motion()
        m.touchDown(0)

        m.drag(49f, 10)

        assertEquals(0, m.frameIndex)
    }

    @Test
    fun `a full turn of drag comes back to the first frame`() {
        val m = motion()
        m.touchDown(0)

        m.drag(3600f, 10)

        assertEquals(0, m.frameIndex)
    }

    // ── Wrap-around ───────────────────────────────────────────────────────────

    @Test
    fun `dragging left from the first frame wraps to the last`() {
        val m = motion()
        m.touchDown(0)

        m.drag(-100f, 10)

        assertEquals(35, m.frameIndex)
    }

    @Test
    fun `dragging right past the last frame wraps to the first`() {
        val m = motion()
        m.touchDown(0)
        m.drag(3500f, 10)
        assertEquals(35, m.frameIndex)

        m.drag(100f, 20)

        assertEquals(0, m.frameIndex)
    }

    @Test
    fun `many turns either way stay in range`() {
        val m = motion()
        m.touchDown(0)

        m.drag(3600f * 25 + 700f, 10)
        assertEquals(7, m.frameIndex)

        m.drag(-3600f * 60 - 900f, 20)
        assertEquals(34, m.frameIndex)
        assertTrue(m.position >= 0f && m.position < 36f)
    }

    @Test
    fun `half a frame short of the end rounds back to the first frame`() {
        val m = motion()
        m.touchDown(0)

        m.drag(-40f, 10) // position 35.6

        assertEquals(0, m.frameIndex)
    }

    // ── Fling ─────────────────────────────────────────────────────────────────

    @Test
    fun `a fling keeps turning after the finger lifts, and slows down`() {
        val m = motion()
        m.touchDown(0)
        m.release(velocityPxPerSec = 1000f, now = 100, animationsEnabled = true) // 10 frames/s

        val v0 = m.velocity
        m.step(200, true)
        val v1 = m.velocity
        m.step(300, true)
        val v2 = m.velocity

        assertEquals(10f, v0, 0.001f)
        assertTrue(v1 in 0f..v0)
        assertTrue(v2 in 0f..v1)
        assertTrue(m.position > 0f)
    }

    @Test
    fun `a fling stops by itself, about v times tau from where it started`() {
        val m = motion(frames = 72, pixelsPerTurn = 7200f)
        m.touchDown(0)
        m.release(1000f, 0, true) // 10 frames/s

        m.run(16, 5_000)

        assertEquals(0f, m.velocity, 0f)
        // ∫ v·e^(-t/τ) = v·τ = 10 · 0.35 = 3.5 frames, less the slow tail cut off.
        assertTrue(abs(m.position - 3.5f) < 0.3f)
    }

    @Test
    fun `the fling distance does not depend on how often frames are drawn`() {
        val coarse = motion(frames = 72, pixelsPerTurn = 7200f)
        coarse.touchDown(0)
        coarse.release(1000f, 0, true)
        coarse.step(100, true)
        coarse.step(200, true)

        val fine = motion(frames = 72, pixelsPerTurn = 7200f)
        fine.touchDown(0)
        fine.release(1000f, 0, true)
        for (t in 10L..200L step 10) fine.step(t, true)

        assertEquals(coarse.position, fine.position, 0.001f)
    }

    @Test
    fun `a leftward fling turns backward and wraps`() {
        val m = motion()
        m.touchDown(0)
        m.release(-1000f, 0, true)

        m.step(100, true)

        assertTrue(m.frameIndex > 30) // wrapped below 0 to the end
    }

    @Test
    fun `a slow release is not a fling`() {
        val m = motion()
        m.touchDown(0)
        m.release(20f, 0, true) // 0.2 frames/s

        assertEquals(0f, m.velocity, 0f)
    }

    @Test
    fun `a very fast fling is capped`() {
        val m = motion()
        m.touchDown(0)
        m.release(1_000_000f, 0, true)

        assertEquals(TurntableMotion.MAX_TURNS_PER_SEC * 36, m.velocity, 0.001f)
    }

    @Test
    fun `a tap stops a fling`() {
        val m = motion()
        m.touchDown(0)
        m.release(1000f, 0, true)
        m.step(50, true)

        m.touchDown(60)
        val stoppedAt = m.position
        m.release(0f, 80, true)
        m.step(200, true)

        assertEquals(0f, m.velocity, 0f)
        assertEquals(stoppedAt, m.position, 0f)
    }

    // ── Auto-rotation ─────────────────────────────────────────────────────────

    @Test
    fun `left alone it starts turning slowly after the idle delay`() {
        val m = motion()
        m.resume(0)

        m.run(0, TurntableMotion.IDLE_MS - 100)
        assertEquals(0f, m.position, 0f)

        m.step(TurntableMotion.IDLE_MS, true)
        val started = m.position
        m.step(TurntableMotion.IDLE_MS + 1_000, true)
        // 36 frames per 20 s turn = 1.8 frames a second.
        assertTrue(started > 0f)
        assertEquals(1.8f, m.position - started, 0.01f)
    }

    @Test
    fun `it does not auto-rotate while a finger is down`() {
        val m = motion()
        m.resume(0)
        m.touchDown(10)

        m.run(10, 30_000)

        assertEquals(0f, m.position, 0f)
        assertNull(m.nextWakeDelayMs(30_000, true))
    }

    @Test
    fun `a resume after a pause does not jump by the time away`() {
        val m = motion()
        m.resume(0)
        m.run(0, 6_000)
        val before = m.position

        m.pause()
        m.resume(100_000)
        m.step(100_016, true)

        assertEquals(before, m.position, 0f)
    }

    @Test
    fun `the wake delay waits for idle, then paces the slow turn, and is next-frame during a fling`() {
        val m = motion()
        m.resume(0)
        m.step(0, true)

        assertEquals(TurntableMotion.IDLE_MS - 1_000, m.nextWakeDelayMs(1_000, true))
        assertEquals(555L, m.nextWakeDelayMs(TurntableMotion.IDLE_MS, true)) // 1000 / 1.8

        m.touchDown(5_000)
        m.release(1000f, 5_010, true)
        assertEquals(0L, m.nextWakeDelayMs(5_010, true))
    }

    // ── Animations off ────────────────────────────────────────────────────────

    @Test
    fun `with animations off there is no fling and no auto-rotation, but drag works`() {
        val m = motion()
        m.resume(0)
        m.touchDown(0)
        m.drag(200f, 10)
        m.release(5000f, 20, animationsEnabled = false)

        m.run(20, 30_000, animations = false)

        assertEquals(2, m.frameIndex)
        assertEquals(0f, m.velocity, 0f)
        assertNull(m.nextWakeDelayMs(30_000, false))
    }

    // ── 0, 1 and many frames ──────────────────────────────────────────────────

    @Test
    fun `one frame never rotates`() {
        val m = motion(frames = 1)
        m.resume(0)
        m.touchDown(0)
        m.drag(5000f, 10)
        m.release(5000f, 20, true)
        m.nudge(3, 30)
        m.run(30, 30_000)

        assertFalse(m.canRotate)
        assertEquals(0, m.frameIndex)
        assertEquals(0f, m.velocity, 0f)
        assertNull(m.nextWakeDelayMs(30_000, true))
    }

    @Test
    fun `no frames never rotates and never divides by zero`() {
        val m = motion(frames = 0)
        m.resume(0)
        m.touchDown(0)
        m.drag(5000f, 10)
        m.release(5000f, 20, true)
        m.nudge(-2, 30)
        m.run(30, 30_000)

        assertFalse(m.canRotate)
        assertEquals(0, m.frameIndex)
        assertEquals(0f, m.position, 0f)
        assertNull(m.nextWakeDelayMs(30_000, true))
    }

    @Test
    fun `a large frame count still maps a full drag to a full turn`() {
        val m = motion(frames = 3600, pixelsPerTurn = 3600f) // 1 px per frame
        m.touchDown(0)

        m.drag(1799f, 10)
        assertEquals(1799, m.frameIndex)

        m.drag(-1800f, 20)
        assertEquals(3599, m.frameIndex)

        m.drag(3600f * 1000, 30)
        assertEquals(3599, m.frameIndex)
    }

    @Test
    fun `nudges turn by whole frames both ways and wrap`() {
        val m = motion(frames = 72)

        m.nudge(9, 0)
        assertEquals(9, m.frameIndex)

        m.nudge(-18, 10)
        assertEquals(63, m.frameIndex)
    }

    @Test
    fun `a tiny width never makes a frame zero pixels wide`() {
        val m = motion(frames = 72, pixelsPerTurn = 0f)
        m.touchDown(0)

        m.drag(1f, 10)

        assertTrue(m.pixelsPerTurn >= 1f)
        assertTrue(m.position >= 0f && m.position < 72f)
    }
}
