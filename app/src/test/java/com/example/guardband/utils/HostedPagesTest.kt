package com.example.guardband.utils

import com.example.guardband.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The join rules for a hosted page URL, plus a check that the two real
 * constants actually produce the URLs the Hosting files are laid out for.
 *
 * Worth testing because the alternative to one builder is a string template
 * copied into every call site, and the failure mode of a wrong one is a 404 on
 * a page that exists to tell people how their data is handled.
 *
 * No Robolectric: `HostedPages` is plain Kotlin, and `BuildConfig` is a
 * generated constant class rather than an Android framework type.
 */
class HostedPagesTest {

    @Test
    fun `host and path are joined with one slash`() {
        assertEquals(
            "https://example.web.app/privacy",
            HostedPages.url("example.web.app", "/privacy")
        )
    }

    /** All four spellings somebody might leave in the build file. */
    @Test
    fun `stray slashes on either side collapse to one`() {
        val expected = "https://example.web.app/privacy"

        assertEquals(expected, HostedPages.url("example.web.app", "privacy"))
        assertEquals(expected, HostedPages.url("example.web.app/", "/privacy"))
        assertEquals(expected, HostedPages.url("example.web.app/", "privacy/"))
        assertEquals(expected, HostedPages.url("example.web.app", "/privacy/"))
    }

    @Test
    fun `a scheme already on the host is not doubled`() {
        assertEquals(
            "https://example.web.app/privacy",
            HostedPages.url("https://example.web.app", "/privacy")
        )
    }

    /** The result is always https, so an http host is upgraded rather than kept. */
    @Test
    fun `an http host is upgraded`() {
        assertEquals(
            "https://example.web.app/privacy",
            HostedPages.url("http://example.web.app", "/privacy")
        )
    }

    @Test
    fun `a blank path gives the bare host`() {
        assertEquals("https://example.web.app", HostedPages.url("example.web.app", ""))
        assertEquals("https://example.web.app", HostedPages.url("example.web.app", "/"))
        assertEquals("https://example.web.app", HostedPages.url("example.web.app", "   "))
    }

    @Test
    fun `a multi-segment path is kept whole`() {
        assertEquals(
            "https://example.web.app/legal/privacy",
            HostedPages.url("example.web.app", "/legal/privacy")
        )
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(
            "https://example.web.app/privacy",
            HostedPages.url("  example.web.app  ", "  /privacy  ")
        )
    }

    // ── The real constants ────────────────────────────────────────────────────

    /**
     * These are the URLs `hosting/public/` is laid out to serve and the ones
     * the Console and the printed notice will carry, so a slip in the build
     * file fails here rather than shipping a dead link.
     */
    @Test
    fun `the real pages resolve to the deployed paths`() {
        assertEquals("https://guardband-aae65.web.app/privacy", HostedPages.privacyNotice)
        assertEquals(
            "https://guardband-aae65.web.app/delete-account",
            HostedPages.deleteAccount
        )
    }

    /** One host for the reset link and both pages, which is the whole point. */
    @Test
    fun `both pages share the host the reset link uses`() {
        assertTrue(HostedPages.privacyNotice.startsWith("https://${BuildConfig.HOSTING_HOST}/"))
        assertTrue(HostedPages.deleteAccount.startsWith("https://${BuildConfig.HOSTING_HOST}/"))
    }
}
