package com.example.guardband.ui.reset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gate on everything that arrives from outside the app.
 *
 * `ResetPasswordActivity` is `exported="true"`, so any app or browser on the
 * device can hand it a URL. The intent filter narrows that, but a filter is
 * not a validator, which is why the parser re-checks the scheme, host, path
 * and mode and why most of these tests are rejections.
 *
 * Plain JVM: the parser takes a `String` and uses `java.net.URI`, not
 * `android.net.Uri`, so none of this needs Robolectric.
 */
class ResetLinkParserTest {

    private fun parse(url: String) =
        ResetLinkParser.oobCodeFrom(url, expectedHost = HOST, expectedPath = PATH)

    // ── Accepted ──────────────────────────────────────────────────────────────

    @Test
    fun `a reset link yields its code`() {
        assertEquals(CODE, parse("https://$HOST$PATH?mode=resetPassword&oobCode=$CODE"))
    }

    /** Firebase sends apiKey and lang too, and may send continueUrl. */
    @Test
    fun `the parameters firebase actually sends are tolerated`() {
        assertEquals(
            CODE,
            parse(
                "https://$HOST$PATH?mode=resetPassword&oobCode=$CODE" +
                    "&apiKey=AIzaSyFake&lang=en&continueUrl=https%3A%2F%2Fexample.com%2Fx"
            )
        )
    }

    @Test
    fun `parameter order does not matter`() {
        assertEquals(CODE, parse("https://$HOST$PATH?oobCode=$CODE&mode=resetPassword"))
    }

    /** DNS names are case-insensitive, so a mail client upper-casing the host is fine. */
    @Test
    fun `the host is matched case-insensitively`() {
        assertEquals(
            CODE,
            parse("https://${HOST.uppercase()}$PATH?mode=resetPassword&oobCode=$CODE")
        )
    }

    /** Some clients append one. */
    @Test
    fun `a single trailing slash on the path is tolerated`() {
        assertEquals(CODE, parse("https://$HOST$PATH/?mode=resetPassword&oobCode=$CODE"))
    }

    /** oobCodes are base64url-ish, so these characters are ordinary, not odd. */
    @Test
    fun `a code with url-safe punctuation survives intact`() {
        val code = "aBc-123_XYZ.456"
        assertEquals(code, parse("https://$HOST$PATH?mode=resetPassword&oobCode=$code"))
    }

    @Test
    fun `a percent-encoded code is decoded`() {
        assertEquals("a b", parse("https://$HOST$PATH?mode=resetPassword&oobCode=a%20b"))
    }

    // ── Rejected ──────────────────────────────────────────────────────────────

    /**
     * Every Firebase email action lands on the same handler path, so these are
     * the links most likely to reach the app by accident.
     */
    @Test
    fun `the wrong mode is refused`() {
        assertNull(parse("https://$HOST$PATH?mode=verifyEmail&oobCode=$CODE"))
        assertNull(parse("https://$HOST$PATH?mode=recoverEmail&oobCode=$CODE"))
        assertNull(parse("https://$HOST$PATH?mode=&oobCode=$CODE"))
    }

    @Test
    fun `a missing mode is refused`() {
        assertNull(parse("https://$HOST$PATH?oobCode=$CODE"))
    }

    @Test
    fun `a missing or empty code is refused`() {
        assertNull(parse("https://$HOST$PATH?mode=resetPassword"))
        assertNull(parse("https://$HOST$PATH?mode=resetPassword&oobCode="))
        assertNull(parse("https://$HOST$PATH?mode=resetPassword&oobCode=%20%20"))
    }

    @Test
    fun `another host is refused`() {
        assertNull(parse("https://evil.example.com$PATH?mode=resetPassword&oobCode=$CODE"))
        // A prefix of the real host is still a different host.
        assertNull(parse("https://guardband-aae65.firebaseapp.com.evil.com$PATH?mode=resetPassword&oobCode=$CODE"))
    }

    /**
     * web.app serves the same Hosting site, but Firebase's reset email links to
     * firebaseapp.com, and the intent filter claims only that host.
     */
    @Test
    fun `the static pages host is refused`() {
        assertNull(parse("https://guardband-aae65.web.app$PATH?mode=resetPassword&oobCode=$CODE"))
    }

    @Test
    fun `another path is refused`() {
        assertNull(parse("https://$HOST/other?mode=resetPassword&oobCode=$CODE"))
        assertNull(parse("https://$HOST/?mode=resetPassword&oobCode=$CODE"))
        assertNull(parse("https://$HOST$PATH/extra?mode=resetPassword&oobCode=$CODE"))
        // The path the app used before it claimed Firebase's handler.
        assertNull(parse("https://$HOST/reset?mode=resetPassword&oobCode=$CODE"))
    }

    /**
     * Firebase's OAuth redirect handler shares the host and the `/__/auth/`
     * prefix, which is why the manifest uses an exact path and never a prefix.
     */
    @Test
    fun `the oauth handler on the same host is refused`() {
        assertNull(parse("https://$HOST/__/auth/handler?mode=resetPassword&oobCode=$CODE"))
    }

    @Test
    fun `plain http is refused`() {
        assertNull(parse("http://$HOST$PATH?mode=resetPassword&oobCode=$CODE"))
    }

    @Test
    fun `no query at all is refused`() {
        assertNull(parse("https://$HOST$PATH"))
    }

    @Test
    fun `an empty string is refused`() {
        assertNull(parse(""))
    }

    @Test
    fun `nonsense is refused rather than thrown`() {
        assertNull(parse("not a url at all"))
        assertNull(parse("https://"))
        assertNull(parse("::::"))
    }

    // ── Odd parameters ────────────────────────────────────────────────────────

    /**
     * First occurrence wins, so a second `mode=` appended to a real link
     * cannot override the one Firebase put there.
     */
    @Test
    fun `a duplicated mode cannot override the first`() {
        assertEquals(
            CODE,
            parse("https://$HOST$PATH?mode=resetPassword&oobCode=$CODE&mode=verifyEmail")
        )
        assertNull(parse("https://$HOST$PATH?mode=verifyEmail&mode=resetPassword&oobCode=$CODE"))
    }

    @Test
    fun `a duplicated code cannot override the first`() {
        assertEquals(
            CODE,
            parse("https://$HOST$PATH?mode=resetPassword&oobCode=$CODE&oobCode=other")
        )
    }

    @Test
    fun `valueless and nameless parameters are ignored`() {
        assertEquals(
            CODE,
            parse("https://$HOST$PATH?flag&=novalue&mode=resetPassword&&oobCode=$CODE")
        )
    }

    /** A broken escape must not blow up on a URL an attacker chose. */
    @Test
    fun `a malformed percent escape is refused rather than thrown`() {
        assertNull(parse("https://$HOST$PATH?mode=resetPassword&oobCode=%zz"))
    }

    /** Case matters for a mode, which is an API value rather than a hostname. */
    @Test
    fun `the mode is case-sensitive`() {
        assertNull(parse("https://$HOST$PATH?mode=ResetPassword&oobCode=$CODE"))
    }

    private companion object {
        /** The real values, so a copy-paste slip in the build file shows up here. */
        const val HOST = "guardband-aae65.firebaseapp.com"
        const val PATH = "/__/auth/action"
        const val CODE = "oob-code-1234"
    }
}
