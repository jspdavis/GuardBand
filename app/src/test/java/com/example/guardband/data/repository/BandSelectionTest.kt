package com.example.guardband.data.repository

import com.example.guardband.testing.FakeBandRepository.Companion.watcher
import com.example.guardband.testing.FakeBandRepository.Companion.wearer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which band the alert screens show, and which is the Device tab's, until
 * Track has its selector.
 */
class BandSelectionTest {

    @Test
    fun `the account's own band is watched first, wherever it is in the list`() {
        val bands = listOf(watcher("friend-1"), wearer("mine"), watcher("friend-2"))

        assertEquals("mine", BandSelection.watchedBandOf(bands))
    }

    @Test
    fun `with no band of its own the first watched band is shown`() {
        val bands = listOf(watcher("friend-1"), watcher("friend-2"))

        assertEquals("friend-1", BandSelection.watchedBandOf(bands))
    }

    @Test
    fun `no bands means nothing to watch`() {
        assertNull(BandSelection.watchedBandOf(emptyList()))
    }

    @Test
    fun `the own band is only ever a worn one`() {
        assertEquals("mine", BandSelection.ownBandOf(listOf(watcher("friend-1"), wearer("mine"))))
        assertNull(BandSelection.ownBandOf(listOf(watcher("friend-1"))))
        assertNull(BandSelection.ownBandOf(emptyList()))
    }

    @Test
    fun `explicit selection is used when it identifies a band in the list`() {
        val bands = listOf(watcher("friend-1"), wearer("mine"), watcher("friend-2"))
        assertEquals("friend-2", BandSelection.watchedBandOf(bands, explicit = "friend-2"))
    }

    @Test
    fun `explicit selection falls back to default when band is not in list`() {
        val bands = listOf(watcher("friend-1"), wearer("mine"))
        assertEquals("mine", BandSelection.watchedBandOf(bands, explicit = "gone"))
    }

    @Test
    fun `explicit null uses default rule`() {
        val bands = listOf(watcher("friend-1"), wearer("mine"))
        assertEquals("mine", BandSelection.watchedBandOf(bands, explicit = null))
    }
}
