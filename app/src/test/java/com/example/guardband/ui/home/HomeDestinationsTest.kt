package com.example.guardband.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home host's destinations and how a saved tag is read back. The Alert
 * tab was removed on 2026-10-19; a restore that still names it, or names
 * nothing, must land on Track rather than crash on an unknown destination.
 */
class HomeDestinationsTest {

    /** The removed Alert tab's tag, as an old saved state would hold it. */
    private val removedAlertTab = "home_alert"

    @Test
    fun `the bar has exactly three tabs, Track first`() {
        assertEquals(
            listOf(HomeDestinations.TRACK, HomeDestinations.CONTACTS, HomeDestinations.PROFILE),
            HomeDestinations.TABS
        )
    }

    @Test
    fun `alert history is a pushed screen, not a tab`() {
        assertFalse(HomeDestinations.isTab(HomeDestinations.ALERT_HISTORY))
        assertTrue(HomeDestinations.ALERT_HISTORY in HomeDestinations.PUSHED)
    }

    @Test
    fun `a saved current tag naming the removed alert tab restores to track`() {
        assertEquals(HomeDestinations.TRACK, HomeDestinations.restoreCurrent(removedAlertTab))
    }

    @Test
    fun `a saved last tab naming the removed alert tab restores to track`() {
        assertEquals(HomeDestinations.TRACK, HomeDestinations.restoreLastTab(removedAlertTab))
    }

    @Test
    fun `nothing saved restores to track`() {
        assertEquals(HomeDestinations.TRACK, HomeDestinations.restoreCurrent(null))
        assertEquals(HomeDestinations.TRACK, HomeDestinations.restoreLastTab(null))
    }

    @Test
    fun `an unknown tag restores to track`() {
        assertEquals(HomeDestinations.TRACK, HomeDestinations.restoreCurrent("home_dashboard"))
        assertEquals(HomeDestinations.TRACK, HomeDestinations.restoreLastTab(""))
    }

    @Test
    fun `every hosted destination restores to itself`() {
        for (tag in HomeDestinations.TABS + HomeDestinations.PUSHED) {
            assertEquals(tag, HomeDestinations.restoreCurrent(tag))
        }
    }

    @Test
    fun `the history screen open at process death comes back as itself`() {
        // Behind the gate's cover again until HomeViewModel reopens it.
        assertEquals(
            HomeDestinations.ALERT_HISTORY,
            HomeDestinations.restoreCurrent(HomeDestinations.ALERT_HISTORY)
        )
    }

    @Test
    fun `a last tab is only ever a tab`() {
        for (tag in HomeDestinations.TABS) {
            assertEquals(tag, HomeDestinations.restoreLastTab(tag))
        }
        for (tag in HomeDestinations.PUSHED) {
            assertEquals(HomeDestinations.TRACK, HomeDestinations.restoreLastTab(tag))
        }
    }
}
