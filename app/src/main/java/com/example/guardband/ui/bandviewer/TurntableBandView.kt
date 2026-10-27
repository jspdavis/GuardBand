package com.example.guardband.ui.bandviewer

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.example.guardband.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The band on a turntable: a [FrameSource]'s frames, turned by a horizontal
 * drag, a fling, the accessibility actions, or slowly by itself when left
 * alone. With fewer than two frames it is a still image (the one frame, or
 * [placeholder] when there are none) that ignores touches. [TurntableMotion]
 * holds the maths; this class draws and handles input.
 *
 * **Frames are decoded lazily, a few at a time.** Only the frame on screen
 * and its neighbours are decoded, on one background thread, into an
 * [LruCache] of [CACHE_FRAMES]. Until the next frame is ready the last one
 * stays on screen. A frame that fails to decode shows [placeholder]; nothing
 * here throws.
 *
 * **Nothing runs unless it is active.** Animation is driven by the view's own
 * frame callbacks (`postInvalidateOnAnimation` during a fling, a single
 * delayed redraw while idle or turning slowly), never a loop, and only while
 * [setActive] is true. Inactive or detached, every callback is removed and
 * every decoded image let go. With the system's animations off (or battery
 * saver, from API 26) there is no fling and no auto-rotation; dragging works.
 *
 * **It only claims horizontal drags.** The touch goes to the parent's
 * `requestDisallowInterceptTouchEvent` only once the finger has clearly moved
 * sideways, so the Device tab's vertical scroll still starts on the band.
 *
 * TalkBack reads [R.string.cd_band_viewer] and offers "Next angle" and
 * "Previous angle" actions, so turning never depends on dragging.
 */
@SuppressLint("ViewConstructor") // Built in code by BandViewer.create, never from XML.
internal class TurntableBandView(
    context: Context,
    private val source: FrameSource,
    private val placeholder: Drawable
) : View(context), BandViewer {

    override val view: View get() = this

    private val motion = TurntableMotion(source.count)

    /** The frame on screen; 0 for a still image. For tests. */
    val currentFrame: Int get() = motion.frameIndex

    /** Whether the band can be turned at all. For tests. */
    val canRotate: Boolean get() = motion.canRotate

    private val cache = LruCache<Int, Bitmap>(CACHE_FRAMES)
    private val requested = HashSet<Int>()
    private val failed = HashSet<Int>()

    /** The last frame drawn, kept on screen while the next one decodes. */
    private var shown: Bitmap? = null

    private var executor: ExecutorService? = null

    /** Bumped whenever images are let go, so a decode that finishes afterwards is dropped. */
    private var generation = 0

    private var active = false
    private var animationsEnabled = true

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false

    /** The "Drag to rotate" hint: shown once per app session, faded on the first drag. */
    private var hintAlpha = if (motion.canRotate && !hintDismissed) 1f else 0f
    private var hintFadeStartedAt: Long? = null

    private val wake = Runnable { invalidate() }

    private val imageBox = Rect()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowCx = 0f
    private var shadowCy = 0f
    private var shadowRx = 0f
    private var shadowRy = 0f

    /** text_secondary, small: the hint under the band. DESIGN: Jul */
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, HINT_TEXT_SP, resources.displayMetrics
        )
    }
    private val hintText = context.getString(R.string.label_band_viewer_hint)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(
            if (motion.canRotate) R.string.cd_band_viewer else R.string.cd_band_viewer_still
        )
        if (motion.canRotate) {
            val angle = max(1, source.count / ACCESSIBILITY_STEPS_PER_TURN)
            ViewCompat.addAccessibilityAction(this, context.getString(R.string.action_band_viewer_next)) { _, _ ->
                turnBy(angle)
                true
            }
            ViewCompat.addAccessibilityAction(this, context.getString(R.string.action_band_viewer_previous)) { _, _ ->
                turnBy(-angle)
                true
            }
        }
    }

    // ── BandViewer ────────────────────────────────────────────────────────────

    override fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        removeCallbacks(wake)
        if (active) {
            motion.resume(SystemClock.uptimeMillis())
            animationsEnabled = animatorsEnabled()
            invalidate()
        } else {
            motion.pause()
            letImagesGo()
        }
    }

    override fun release() {
        active = false
        motion.pause()
        removeCallbacks(wake)
        letImagesGo()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        motion.pixelsPerTurn = w * WIDTHS_PER_TURN

        val left = paddingLeft
        val right = w - paddingRight
        val top = paddingTop
        val bottom = h - paddingBottom
        val hintSpace = (hintPaint.textSize * 2).toInt()
        val contentBottom = bottom - hintSpace
        imageBox.set(left, top, right, contentBottom - (contentBottom - top) / 12)

        // A soft ground shadow (a glow in dark mode: text_primary is light
        // there) under the band. DESIGN: Jul
        shadowCx = (left + right) / 2f
        shadowCy = imageBox.bottom.toFloat()
        shadowRx = (right - left) * 0.28f
        shadowRy = max(1f, (contentBottom - top) * 0.05f)
        val tint = ContextCompat.getColor(context, R.color.text_primary)
        shadowPaint.shader = if (shadowRx > 0f) {
            RadialGradient(
                0f, 0f, shadowRx,
                intArrayOf(ColorUtils.setAlphaComponent(tint, SHADOW_ALPHA), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        if (active) motion.step(now, animationsEnabled)

        drawShadow(canvas)
        val bitmap = frameToDraw()
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, fitCenter(bitmap.width, bitmap.height), bitmapPaint)
        } else if (source.count == 0 || motion.frameIndex in failed) {
            placeholder.bounds = fitCenter(placeholder.intrinsicWidth, placeholder.intrinsicHeight)
            placeholder.draw(canvas)
        }
        drawHint(canvas, now)

        if (active) scheduleNext(now)
    }

    private fun drawShadow(canvas: Canvas) {
        if (shadowPaint.shader == null) return
        canvas.save()
        canvas.translate(shadowCx, shadowCy)
        canvas.scale(1f, shadowRy / shadowRx)
        canvas.drawCircle(0f, 0f, shadowRx, shadowPaint)
        canvas.restore()
    }

    private fun drawHint(canvas: Canvas, now: Long) {
        val start = hintFadeStartedAt
        if (start != null) {
            hintAlpha = (1f - (now - start).toFloat() / HINT_FADE_MS).coerceAtLeast(0f)
            if (hintAlpha <= 0f) hintFadeStartedAt = null
        }
        if (hintAlpha <= 0f) return
        hintPaint.alpha = (255 * hintAlpha).toInt()
        val baseline = height - paddingBottom - hintPaint.descent()
        canvas.drawText(hintText, width / 2f, baseline, hintPaint)
    }

    /** [width] × [height] scaled to fit [imageBox], centred. Never upscaled past twice. */
    private fun fitCenter(width: Int, height: Int): Rect {
        if (width <= 0 || height <= 0 || imageBox.isEmpty) return Rect(imageBox)
        val scale = min(
            min(imageBox.width().toFloat() / width, imageBox.height().toFloat() / height),
            MAX_UPSCALE
        )
        val w = (width * scale).toInt()
        val h = (height * scale).toInt()
        val left = imageBox.centerX() - w / 2
        // Stand the band on its shadow.
        val top = imageBox.bottom - h
        return Rect(left, top, left + w, top + h)
    }

    /**
     * The decoded frame for the current position, asking for it (and,
     * while active, its neighbours) when it is not ready yet; meanwhile the
     * last frame drawn. Null means draw the placeholder or nothing.
     */
    private fun frameToDraw(): Bitmap? {
        if (source.count == 0) return null
        val index = motion.frameIndex
        if (index in failed) return null
        val ready = cache.get(index)
        if (ready != null) {
            shown = ready
        } else {
            request(index)
        }
        if (active && motion.canRotate) {
            for (offset in 1..PREFETCH_EACH_SIDE) {
                request(Math.floorMod(index + offset, source.count))
                request(Math.floorMod(index - offset, source.count))
            }
        }
        return shown
    }

    private fun request(index: Int) {
        if (index in requested || index in failed || cache.get(index) != null) return
        requested += index
        val ticket = generation
        val worker = executor ?: Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "band-viewer").apply { isDaemon = true }
        }.also { executor = it }
        worker.execute {
            val bitmap = source.decode(index)
            post {
                if (ticket != generation) return@post
                requested -= index
                if (bitmap == null) failed += index else cache.put(index, bitmap)
                invalidate()
            }
        }
    }

    private fun letImagesGo() {
        generation++
        executor?.shutdownNow()
        executor = null
        requested.clear()
        cache.evictAll()
        shown = null
    }

    private fun scheduleNext(now: Long) {
        removeCallbacks(wake)
        if (hintFadeStartedAt != null && hintAlpha > 0f) {
            postInvalidateOnAnimation()
            return
        }
        when (val delay = motion.nextWakeDelayMs(now, animationsEnabled)) {
            null -> Unit
            0L -> postInvalidateOnAnimation()
            else -> postDelayed(wake, delay)
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility") // performClick is called on a tap.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!motion.canRotate) return super.onTouchEvent(event)
        val now = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                // A tap stops the spin.
                motion.touchDown(now)
                removeCallbacks(wake)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!dragging) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    // Claim the gesture only once it is clearly sideways; a
                    // vertical one is left to the parent ScrollView, which
                    // intercepts it and sends this view a cancel.
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        dragging = true
                        lastX = event.x
                        parent?.requestDisallowInterceptTouchEvent(true)
                        dismissHint(now)
                    }
                }
                if (dragging) {
                    motion.drag(event.x - lastX, now)
                    lastX = event.x
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val tracker = velocityTracker
                tracker?.addMovement(event)
                if (dragging && tracker != null) {
                    tracker.computeCurrentVelocity(1000)
                    motion.release(tracker.xVelocity, now, animationsEnabled)
                } else {
                    motion.release(0f, now, animationsEnabled)
                    performClick()
                }
                endGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                motion.release(0f, now, animationsEnabled)
                endGesture()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun endGesture() {
        dragging = false
        velocityTracker?.recycle()
        velocityTracker = null
        invalidate()
    }

    private fun turnBy(frames: Int) {
        motion.nudge(frames, SystemClock.uptimeMillis())
        dismissHint(SystemClock.uptimeMillis())
        invalidate()
    }

    private fun dismissHint(now: Long) {
        hintDismissed = true
        if (hintAlpha <= 0f || hintFadeStartedAt != null) return
        if (animationsEnabled) {
            hintFadeStartedAt = now
        } else {
            hintAlpha = 0f
        }
    }

    private fun animatorsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) != 0f
        }

    companion object {
        /**
         * Whether the hint has been dismissed in this process: memory only,
         * never stored, so it shows again after the app is restarted.
         */
        private var hintDismissed = false

        /** Decoded frames kept: the one on screen and its neighbours. */
        private const val CACHE_FRAMES = 6

        /** Neighbours decoded ahead on each side. */
        private const val PREFETCH_EACH_SIDE = 2

        /** One full turn per this many view widths of drag. */
        private const val WIDTHS_PER_TURN = 1.5f

        /** "Next angle" turns an eighth of a turn. */
        private const val ACCESSIBILITY_STEPS_PER_TURN = 8

        /** The ground shadow's strength at its centre. DESIGN: Jul */
        private const val SHADOW_ALPHA = 0x33

        private const val HINT_TEXT_SP = 12f
        private const val HINT_FADE_MS = 400f

        /** A small frame is not blown up past this. */
        private const val MAX_UPSCALE = 2f
    }
}
