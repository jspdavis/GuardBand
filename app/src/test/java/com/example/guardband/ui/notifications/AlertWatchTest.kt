package com.example.guardband.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

/** The watch runs only when all three hold, and is never started otherwise. */
class AlertWatchTest {

    @Test
    fun `the watch runs only when switched on, signed in and allowed to post`() {
        for (enabled in listOf(true, false)) {
            for (signedIn in listOf(true, false)) {
                for (allowed in listOf(true, false)) {
                    assertEquals(
                        "enabled=$enabled signedIn=$signedIn allowed=$allowed",
                        enabled && signedIn && allowed,
                        AlertWatch.shouldRun(enabled, signedIn, allowed)
                    )
                }
            }
        }
    }
}
