package com.example.guardband.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MockSenderLink] holds the one spelling of the way into the debug-only
 * harness. Building an `android.content.Intent` needs a real Android class,
 * hence Robolectric; the object itself holds no `Context`.
 *
 * [MockSenderLink.isAvailable] is `BuildConfig.DEBUG`, which is true under
 * the debug unit-test variant, so it is not asserted here: a test of it would
 * only restate the build variant it runs in.
 */
@RunWith(RobolectricTestRunner::class)
class MockSenderLinkTest {

    @Test
    fun `the intent carries the harness action`() {
        assertEquals(
            MockSenderLink.ACTION,
            MockSenderLink.intent("com.example.guardband").action
        )
    }

    @Test
    fun `the intent is restricted to the given package`() {
        // Without the package, any app on the phone declaring the action
        // could answer it. This is the line that keeps it ours.
        assertEquals(
            "com.example.guardband",
            MockSenderLink.intent("com.example.guardband").`package`
        )
    }

    @Test
    fun `the action is namespaced to the app, not a reused Android one`() {
        assertEquals("com.example.guardband.action.MOCK_SENDER", MockSenderLink.ACTION)
    }
}
