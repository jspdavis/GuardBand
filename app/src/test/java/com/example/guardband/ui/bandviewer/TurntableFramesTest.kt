package com.example.guardband.ui.bandviewer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which files in the frame folder are frames, and in what order. */
class TurntableFramesTest {

    @Test
    fun `frames are ordered by their number, not alphabetically by accident`() {
        val names = (0 until 72).map { "band_turn_%03d.webp".format(it) }.shuffled()

        val frames = TurntableFrames.select(names)

        assertEquals(72, frames.size)
        assertEquals("band_turn_000.webp", frames.first())
        assertEquals("band_turn_071.webp", frames.last())
        assertEquals("band_turn_010.webp", frames[10])
    }

    @Test
    fun `anything that is not a numbered webp frame is ignored`() {
        val frames = TurntableFrames.select(
            listOf(
                "README.md",
                "band_turn_000.png",
                "band_turn_1.webp",
                "band_turn_0001.webp",
                "Band_Turn_002.webp",
                "band_turn_000.webp",
                "band_turn_001.webp"
            )
        )

        assertEquals(listOf("band_turn_000.webp", "band_turn_001.webp"), frames)
    }

    @Test
    fun `an empty or missing folder has no frames`() {
        assertEquals(emptyList<String>(), TurntableFrames.select(emptyList()))
    }

    @Test
    fun `a gap in the numbering is played in order, not an error`() {
        val frames = TurntableFrames.select(listOf("band_turn_002.webp", "band_turn_000.webp"))

        assertEquals(listOf("band_turn_000.webp", "band_turn_002.webp"), frames)
    }
}
