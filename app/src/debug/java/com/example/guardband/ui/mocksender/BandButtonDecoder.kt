package com.example.guardband.ui.mocksender

/**
 * The band's one physical button, decoded the way the firmware does it:
 *
 *  - **Hold [panicHoldMs] (1.5 s) → panic**, fired the moment the hold reaches
 *    it, without waiting for release: someone in trouble may not let go.
 *  - **Two presses within [doublePressMs] (2 s) → check-in** (FR-10), sent on
 *    the second press.
 *  - **While a panic is being tracked, hold [cancelHoldMs] (5 s) → cancel**:
 *    tracking stops and a check-in is sent. A hold that long can be neither
 *    an accident nor quick to force; a second panic is never sent, since one
 *    is already running.
 *  - **One press alone → nothing**, so a bump sends nothing.
 *
 * A press counts toward a double-press only if it is let go before
 * [panicHoldMs]; anything longer is a hold.
 *
 * Pure and time-driven: every call takes the current time (a monotonic
 * clock, e.g. `SystemClock.elapsedRealtime`), and the caller schedules the
 * deadlines it is told about. So the rules are plain JVM tests
 * (`BandButtonDecoderTest`) and the Activity only wires timers to it.
 * Not for the main app: this is the debug harness standing in for the band.
 */
class BandButtonDecoder(
    val panicHoldMs: Long = PANIC_HOLD_MS,
    val cancelHoldMs: Long = CANCEL_HOLD_MS,
    val doublePressMs: Long = DOUBLE_PRESS_MS
) {

    /** What a touch, a hold reaching its time, or a lapsed window amounts to. */
    enum class Gesture {
        /** Hold reached [panicHoldMs] with no panic running: send a panic, start tracking. */
        PANIC,

        /** Second press within [doublePressMs]: send a check-in. */
        CHECK_IN,

        /** Hold reached [cancelHoldMs] during a panic: stop tracking, send a check-in. */
        CANCEL_PANIC,

        /** A first press: waiting up to [doublePressMs] for a second. */
        FIRST_PRESS,

        /** The first press was not followed by a second in time: nothing is sent. */
        SINGLE_PRESS,

        /** During a panic, a hold let go before [cancelHoldMs]: nothing is sent. */
        CANCEL_TOO_SHORT
    }

    /** When the button went down, or null while it is up. */
    private var downAt: Long? = null

    /** Whether a panic was being tracked when the button went down. */
    private var trackingAtDown = false

    /** Whether this hold has already fired, so its release does nothing. */
    private var holdFired = false

    /** When the first press of a possible double-press was let go. */
    private var firstPressAt: Long? = null

    /**
     * The button went down. Returns how long from now the hold fires: the
     * caller calls [holdReached] then, unless [up] came first.
     */
    fun down(now: Long, tracking: Boolean): Long {
        downAt = now
        trackingAtDown = tracking
        holdFired = false
        return holdNeeded()
    }

    /**
     * The hold's deadline came. [Gesture.PANIC], or [Gesture.CANCEL_PANIC]
     * during a panic, when the button is still down and has been for long
     * enough; null otherwise (a late timer after the release, say).
     */
    fun holdReached(now: Long): Gesture? {
        val since = downAt ?: return null
        if (holdFired || now - since < holdNeeded()) return null
        holdFired = true
        firstPressAt = null
        return if (trackingAtDown) Gesture.CANCEL_PANIC else Gesture.PANIC
    }

    /**
     * The button came up. [Gesture.CHECK_IN] on a second quick press,
     * [Gesture.FIRST_PRESS] on a first (the caller then calls [windowLapsed]
     * after [doublePressMs]), [Gesture.CANCEL_TOO_SHORT] for a too-short
     * cancel hold, and null after a hold that already fired.
     */
    fun up(now: Long): Gesture? {
        val since = downAt ?: return null
        downAt = null
        if (holdFired) return null
        if (now - since >= panicHoldMs) {
            // Only reachable during a panic: otherwise the hold would have fired.
            firstPressAt = null
            return Gesture.CANCEL_TOO_SHORT
        }
        val first = firstPressAt
        return if (first != null && now - first <= doublePressMs) {
            firstPressAt = null
            Gesture.CHECK_IN
        } else {
            firstPressAt = now
            Gesture.FIRST_PRESS
        }
    }

    /**
     * The double-press window's deadline came. [Gesture.SINGLE_PRESS] when
     * the first press is still waiting alone and its window is over; null
     * otherwise (a second press already came, or the button is down again).
     */
    fun windowLapsed(now: Long): Gesture? {
        val first = firstPressAt ?: return null
        if (downAt != null || now - first < doublePressMs) return null
        firstPressAt = null
        return Gesture.SINGLE_PRESS
    }

    /** The touch was taken away (the system cancelled it): forget the press, send nothing. */
    fun cancel() {
        downAt = null
        holdFired = false
    }

    private fun holdNeeded(): Long = if (trackingAtDown) cancelHoldMs else panicHoldMs

    companion object {
        /** The band's panic hold, from the firmware: 1.5 s, firing while held. */
        const val PANIC_HOLD_MS = 1_500L

        /** During a panic, the hold that cancels it. */
        const val CANCEL_HOLD_MS = 5_000L

        /** FR-10 and SCHEMA.md: the second press within 2 s. */
        const val DOUBLE_PRESS_MS = 2_000L
    }
}
