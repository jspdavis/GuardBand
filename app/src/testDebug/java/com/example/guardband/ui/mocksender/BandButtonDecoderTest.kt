package com.example.guardband.ui.mocksender

import com.example.guardband.ui.mocksender.BandButtonDecoder.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Mock Sender's band button, decoded with the firmware's timings: hold
 * 1.5 s for a panic (fired while held), two presses within 2 s for a
 * check-in, and during a panic a 5 s hold to cancel it.
 *
 * In `testDebug` because the decoder is debug-only code, like the Mock
 * Sender itself. Times are plain numbers: nothing waits.
 */
class BandButtonDecoderTest {

    private val decoder = BandButtonDecoder()

    /** A full press from [at], let go [lengthMs] later. */
    private fun press(at: Long, lengthMs: Long = 100, tracking: Boolean = false): Gesture? {
        decoder.down(at, tracking)
        return decoder.up(at + lengthMs)
    }

    // ── Panic ─────────────────────────────────────────────────────────────────

    @Test
    fun `the hold is timed for 1_5 s, and fires a panic while still held`() {
        assertEquals(1_500L, decoder.down(0, tracking = false))

        assertEquals(Gesture.PANIC, decoder.holdReached(1_500))
        // Letting go afterwards sends nothing more.
        assertNull(decoder.up(4_000))
    }

    @Test
    fun `a timer that fires early, or twice, sends one panic`() {
        decoder.down(0, tracking = false)

        assertNull(decoder.holdReached(1_499))
        assertEquals(Gesture.PANIC, decoder.holdReached(1_500))
        assertNull(decoder.holdReached(1_600))
    }

    @Test
    fun `letting go before 1_5 s is a press, not a panic`() {
        decoder.down(0, tracking = false)

        assertEquals(Gesture.FIRST_PRESS, decoder.up(1_499))
        assertNull(decoder.holdReached(1_500))
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    @Test
    fun `two presses within 2 s are a check-in`() {
        assertEquals(Gesture.FIRST_PRESS, press(at = 0))
        assertEquals(Gesture.CHECK_IN, press(at = 1_900))
        assertNull(decoder.windowLapsed(2_200))
    }

    @Test
    fun `a second press after 2 s starts again instead`() {
        assertEquals(Gesture.FIRST_PRESS, press(at = 0))
        assertEquals(Gesture.SINGLE_PRESS, decoder.windowLapsed(2_100))
        assertEquals(Gesture.FIRST_PRESS, press(at = 2_500))
    }

    @Test
    fun `a press too late for the window, with its timer late too, is a new first press`() {
        assertEquals(Gesture.FIRST_PRESS, press(at = 0))
        assertEquals(Gesture.FIRST_PRESS, press(at = 2_200))
    }

    @Test
    fun `three quick presses are one check-in and a new first press`() {
        assertEquals(Gesture.FIRST_PRESS, press(at = 0))
        assertEquals(Gesture.CHECK_IN, press(at = 400))
        assertEquals(Gesture.FIRST_PRESS, press(at = 800))
    }

    @Test
    fun `a single press sends nothing once its window is over`() {
        assertEquals(Gesture.FIRST_PRESS, press(at = 0))

        assertNull(decoder.windowLapsed(1_000))
        assertEquals(Gesture.SINGLE_PRESS, decoder.windowLapsed(2_100))
        assertNull(decoder.windowLapsed(2_200))
    }

    @Test
    fun `a panic between two presses leaves no check-in pending`() {
        assertEquals(Gesture.FIRST_PRESS, press(at = 0))
        decoder.down(300, tracking = false)
        assertEquals(Gesture.PANIC, decoder.holdReached(1_800))
        assertNull(decoder.up(2_000))

        assertEquals(Gesture.FIRST_PRESS, press(at = 2_100))
    }

    // ── Cancel, during a panic ────────────────────────────────────────────────

    @Test
    fun `during a panic the hold is timed for 5 s and cancels instead`() {
        assertEquals(5_000L, decoder.down(0, tracking = true))

        assertNull(decoder.holdReached(1_500))
        assertEquals(Gesture.CANCEL_PANIC, decoder.holdReached(5_000))
        assertNull(decoder.up(6_000))
    }

    @Test
    fun `during a panic a hold let go before 5 s sends nothing, not a second panic`() {
        decoder.down(0, tracking = true)

        assertEquals(Gesture.CANCEL_TOO_SHORT, decoder.up(3_000))
        assertNull(decoder.holdReached(5_000))
    }

    @Test
    fun `during a panic a double-press is still a check-in`() {
        assertEquals(Gesture.FIRST_PRESS, press(at = 0, tracking = true))
        assertEquals(Gesture.CHECK_IN, press(at = 500, tracking = true))
    }

    // ── Cancelled touches ─────────────────────────────────────────────────────

    @Test
    fun `a touch the system cancels sends nothing`() {
        decoder.down(0, tracking = false)
        decoder.cancel()

        assertNull(decoder.holdReached(1_500))
        assertNull(decoder.up(1_600))
    }

    @Test
    fun `the timings are the firmware's`() {
        assertEquals(1_500L, BandButtonDecoder.PANIC_HOLD_MS)
        assertEquals(5_000L, BandButtonDecoder.CANCEL_HOLD_MS)
        assertEquals(2_000L, BandButtonDecoder.DOUBLE_PRESS_MS)
    }
}
