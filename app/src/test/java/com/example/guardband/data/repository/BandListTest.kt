package com.example.guardband.data.repository

import com.example.guardband.data.DeviceConstants
import com.example.guardband.testing.FakeBandRepository.Companion.watcher
import com.example.guardband.testing.FakeBandRepository.Companion.wearer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which bands an account has: the rule that, wrong, emptied every screen
 * from 2026-11-10 to 2026-11-11 while every other test stayed green.
 */
class BandListTest {

    private val demo = DeviceConstants.DEFAULT_DEVICE_ID

    @Test
    fun `an account that has claimed nothing wears the demo band, and only that`() {
        assertEquals(listOf(wearer(demo)), BandList.of(claimed = emptyList(), watched = emptyList()))
    }

    @Test
    fun `a claimed band replaces the demo band`() {
        assertEquals(listOf(wearer("bandA")), BandList.of(claimed = listOf("bandA"), watched = emptyList()))
    }

    @Test
    fun `a claimed band comes first, so the default rule shows it`() {
        val bands = BandList.of(claimed = listOf("bandA"), watched = listOf("bandB"))

        assertEquals(listOf(wearer("bandA"), watcher("bandB")), bands)
        assertEquals("bandA", BandSelection.watchedBandOf(bands))
    }

    @Test
    fun `with nothing claimed the default rule shows the demo band, even with friends`() {
        val bands = BandList.of(claimed = emptyList(), watched = listOf("bandB"))

        assertEquals("$demo first", demo, BandSelection.watchedBandOf(bands))
        assertEquals(demo, BandSelection.ownBandOf(bands))
    }

    @Test
    fun `a band is listed once, and worn wins over watched`() {
        val bands = BandList.of(claimed = listOf("bandA", "bandA"), watched = listOf("bandA", "bandB", "bandB"))

        assertEquals(listOf(wearer("bandA"), watcher("bandB")), bands)
    }

    @Test
    fun `a friend on the demo band is not listed twice`() {
        val bands = BandList.of(claimed = emptyList(), watched = listOf(demo))

        assertEquals(listOf(wearer(demo)), bands)
    }
}
