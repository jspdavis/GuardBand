package com.example.guardband.ui.bandviewer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The turntable's rotation maths: which frame shows, how a drag and a fling
 * move it, and when it turns by itself. Pure, with every time passed in, so
 * it is a plain JVM test; [TurntableBandView] feeds it touches and the
 * view's own frame callbacks.
 *
 * **The position is in frames**, a Float kept in `[0, frameCount)`: 0 is the
 * first frame, `frameCount - 1` the last, and anything between rounds to the
 * nearest. One full turn is `frameCount` frames, so nothing here depends on
 * how many frames there are; the count comes from the frame folder.
 *
 * **Direction:** dragging right increases the position. The frames are
 * rendered so each next one has the band turned with its front moving to the
 * viewer's right (docs/band-viewer.md §2), so the front follows the finger.
 *
 * With fewer than two frames nothing moves: [canRotate] is false and every
 * input is ignored.
 *
 * @param frameCount how many frames the turntable has; 0 or 1 is a still image.
 * @param pixelsPerTurn how far a drag goes for one full turn. The view sets it
 *   from its width.
 */
class TurntableMotion(
    val frameCount: Int,
    pixelsPerTurn: Float = DEFAULT_PIXELS_PER_TURN
) {

    /** Whether there is anything to turn. */
    val canRotate: Boolean get() = frameCount > 1

    /** Drag distance for one full turn; never below 1 px. */
    var pixelsPerTurn: Float = pixelsPerTurn.coerceAtLeast(1f)
        set(value) { field = value.coerceAtLeast(1f) }

    /** Where the turntable is, in frames, in `[0, frameCount)`. */
    var position: Float = 0f
        private set

    /** Spin left over from a fling, in frames per second; 0 when still. */
    var velocity: Float = 0f
        private set

    /** A finger is on the band: no fling and no auto-rotation until it lifts. */
    var isTouching: Boolean = false
        private set

    /** When the user last touched or turned it; auto-rotation waits [IDLE_MS] from here. */
    private var lastInteractionAt: Long = 0L

    /** When [step] last ran; null after [pause], so a resume never jumps. */
    private var lastStepAt: Long? = null

    /** The frame to show: the position rounded, wrapped at both ends. 0 for a still image. */
    val frameIndex: Int
        get() = if (!canRotate) 0 else Math.floorMod(position.roundToInt(), frameCount)

    private val pixelsPerFrame: Float get() = pixelsPerTurn / frameCount

    /** A finger went down: any spin stops (a tap stops it). */
    fun touchDown(now: Long) {
        isTouching = true
        velocity = 0f
        lastInteractionAt = now
    }

    /** The finger moved [dxPx] to the right (negative: left). */
    fun drag(dxPx: Float, now: Long) {
        lastInteractionAt = now
        if (!canRotate) return
        velocity = 0f
        moveBy(dxPx / pixelsPerFrame)
    }

    /**
     * The finger lifted at [velocityPxPerSec]. With [animationsEnabled] the
     * spin continues and decays ([step]); without, it stops where it is.
     */
    fun release(velocityPxPerSec: Float, now: Long, animationsEnabled: Boolean) {
        isTouching = false
        lastInteractionAt = now
        lastStepAt = now
        if (!canRotate || !animationsEnabled) {
            velocity = 0f
            return
        }
        val framesPerSec = (velocityPxPerSec / pixelsPerFrame)
            .coerceIn(-MAX_TURNS_PER_SEC * frameCount, MAX_TURNS_PER_SEC * frameCount)
        velocity = if (abs(framesPerSec) < MIN_FLING_FRAMES_PER_SEC) 0f else framesPerSec
    }

    /** Turns by whole [frames] at once (the accessibility actions), and stops any spin. */
    fun nudge(frames: Int, now: Long) {
        lastInteractionAt = now
        velocity = 0f
        if (canRotate) moveBy(frames.toFloat())
    }

    /** The view stopped drawing (tab hidden, app in the background): forget the clock. */
    fun pause() {
        lastStepAt = null
        velocity = 0f
    }

    /** The view is on screen again at [now]: auto-rotation waits [IDLE_MS] from here. */
    fun resume(now: Long) {
        pause()
        lastInteractionAt = now
    }

    /**
     * Advances to [now]: a fling decays (`v · e^(-t/τ)`, τ = [FLING_TIME_CONSTANT_MS]),
     * and after [IDLE_MS] without a touch the band turns slowly by itself,
     * one full turn every [AUTO_TURN_MS]. Neither happens without
     * [animationsEnabled]. Returns whether the frame changed.
     */
    fun step(now: Long, animationsEnabled: Boolean): Boolean {
        val previous = lastStepAt
        lastStepAt = now
        if (!canRotate || previous == null || isTouching) return false
        val dtMs = (now - previous).coerceAtLeast(0L)
        if (dtMs == 0L) return false
        if (!animationsEnabled) {
            velocity = 0f
            return false
        }

        val before = frameIndex
        if (velocity != 0f) {
            // Exact integral of v·e^(-t/τ) over dt, so the distance does not
            // depend on how often frames are drawn.
            val decay = exp(-dtMs.toFloat() / FLING_TIME_CONSTANT_MS)
            moveBy(velocity * (FLING_TIME_CONSTANT_MS / 1000f) * (1f - decay))
            velocity *= decay
            if (abs(velocity) < MIN_FLING_FRAMES_PER_SEC) {
                velocity = 0f
                lastInteractionAt = now
            }
        } else if (now - lastInteractionAt >= IDLE_MS) {
            moveBy(autoFramesPerSec * dtMs / 1000f)
        }
        return frameIndex != before
    }

    /**
     * How long the view may wait before calling [step] again: 0 means on the
     * next frame (a fling is running), a positive number means that many
     * milliseconds (waiting to idle, or turning slowly), and null means it
     * may stop asking until something touches it.
     */
    fun nextWakeDelayMs(now: Long, animationsEnabled: Boolean): Long? {
        if (!canRotate || !animationsEnabled || isTouching) return null
        if (velocity != 0f) return 0L
        val idleLeft = IDLE_MS - (now - lastInteractionAt)
        if (idleLeft > 0) return idleLeft
        return (1000f / autoFramesPerSec).toLong().coerceAtLeast(1L)
    }

    private val autoFramesPerSec: Float get() = frameCount * 1000f / AUTO_TURN_MS

    private fun moveBy(frames: Float) {
        val n = frameCount.toFloat()
        val moved = position + frames
        position = moved - floor(moved / n) * n
        // Float rounding can land exactly on n; that is frame 0.
        if (position >= n) position = 0f
    }

    companion object {
        /** A drag this long turns it once, before the view knows its width. */
        const val DEFAULT_PIXELS_PER_TURN = 1000f

        /** How long a fling takes to lose about two thirds of its speed. */
        const val FLING_TIME_CONSTANT_MS = 350f

        /** Slower than this, a fling stops. */
        const val MIN_FLING_FRAMES_PER_SEC = 0.5f

        /** The fastest a fling may spin. */
        const val MAX_TURNS_PER_SEC = 3f

        /** Untouched this long, the band starts turning by itself. */
        const val IDLE_MS = 4_000L

        /** One full turn of auto-rotation, whatever the frame count. */
        const val AUTO_TURN_MS = 20_000L
    }
}
