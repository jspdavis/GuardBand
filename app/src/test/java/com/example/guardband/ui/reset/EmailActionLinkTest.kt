package com.example.guardband.ui.reset

import com.example.guardband.ui.reset.EmailActionLink.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which screen a Firebase email link belongs to, and the verify-email code.
 *
 * Every email action arrives at the same handler URL, so this routing is what
 * keeps a verify link off the reset screen - and, just as importantly, what
 * keeps **every reset link on it, exactly as before**. The reset half is
 * proved twice: here, by routing every reset-shaped and malformed link to
 * [Route.RESET_SCREEN], and by `ResetLinkParserTest`, unchanged, which now runs
 * through the shared checks.
 *
 * Plain JVM, like the parser.
 */
class EmailActionLinkTest {

    private fun route(url: String) = EmailActionLink.routeOf(url, HOST, PATH)
    private fun verifyCode(url: String) = EmailActionLink.verifyEmailCodeFrom(url, HOST, PATH)
    private fun resetCode(url: String) = ResetLinkParser.oobCodeFrom(url, HOST, PATH)

    // ── The reset path is untouched ───────────────────────────────────────────

    @Test
    fun `a reset link stays on the reset screen and still yields its code`() {
        val url = "https://$HOST$PATH?mode=resetPassword&oobCode=$CODE&apiKey=k&lang=en"

        assertEquals(Route.RESET_SCREEN, route(url))
        assertEquals(CODE, resetCode(url))
        assertNull(verifyCode(url))
    }

    /** Malformed or foreign links still reach the reset screen's own "isn't valid". */
    @Test
    fun `anything that is not a well-formed action link takes today's path`() {
        listOf(
            "",
            "not a url",
            "http://$HOST$PATH?mode=verifyEmail&oobCode=$CODE",
            "https://evil.example.com$PATH?mode=verifyEmail&oobCode=$CODE",
            "https://guardband-aae65.web.app$PATH?mode=verifyEmail&oobCode=$CODE",
            "https://$HOST/__/auth/handler?mode=verifyEmail&oobCode=$CODE",
            "https://$HOST$PATH",
            "https://$HOST$PATH?oobCode=$CODE",
            "https://$HOST$PATH?mode=&oobCode=$CODE"
        ).forEach { url ->
            assertEquals(url, Route.RESET_SCREEN, route(url))
            assertNull(url, verifyCode(url))
        }
    }

    /** First occurrence wins, so an appended mode cannot reroute a reset link. */
    @Test
    fun `an appended verifyEmail cannot pull a reset link off the reset screen`() {
        val url = "https://$HOST$PATH?mode=resetPassword&oobCode=$CODE&mode=verifyEmail"

        assertEquals(Route.RESET_SCREEN, route(url))
        assertEquals(CODE, resetCode(url))
    }

    // ── Verify email ──────────────────────────────────────────────────────────

    @Test
    fun `a verify link goes to the verify screen with its code`() {
        val url = "https://$HOST$PATH?mode=verifyEmail&oobCode=$CODE&apiKey=k&lang=en"

        assertEquals(Route.VERIFY_EMAIL, route(url))
        assertEquals(CODE, verifyCode(url))
        // And the reset parser still refuses it, should it ever get there.
        assertNull(resetCode(url))
    }

    @Test
    fun `a verify link with a trailing slash and an upper-case host is accepted`() {
        val url = "https://GUARDBAND-AAE65.FIREBASEAPP.COM$PATH/?mode=verifyEmail&oobCode=$CODE"

        assertEquals(Route.VERIFY_EMAIL, route(url))
        assertEquals(CODE, verifyCode(url))
    }

    @Test
    fun `a verify link with no code has no code`() {
        assertNull(verifyCode("https://$HOST$PATH?mode=verifyEmail"))
        assertNull(verifyCode("https://$HOST$PATH?mode=verifyEmail&oobCode="))
        assertNull(verifyCode("https://$HOST$PATH?mode=verifyEmail&oobCode=%20"))
    }

    @Test
    fun `a malformed escape in the code is no code, not a crash`() {
        assertNull(verifyCode("https://$HOST$PATH?mode=verifyEmail&oobCode=%zz"))
    }

    // ── Modes the app does not handle ─────────────────────────────────────────

    @Test
    fun `other firebase email actions are unsupported, not invalid resets`() {
        listOf("recoverEmail", "verifyAndChangeEmail", "revertSecondFactorAddition", "signIn")
            .forEach { mode ->
                val url = "https://$HOST$PATH?mode=$mode&oobCode=$CODE"
                assertEquals(mode, Route.UNSUPPORTED, route(url))
                assertNull(mode, verifyCode(url))
                assertNull(mode, resetCode(url))
            }
    }

    @Test
    fun `mode is matched exactly`() {
        assertEquals(Route.UNSUPPORTED, route("https://$HOST$PATH?mode=VerifyEmail&oobCode=$CODE"))
        assertEquals(Route.UNSUPPORTED, route("https://$HOST$PATH?mode=resetpassword&oobCode=$CODE"))
    }

    private companion object {
        const val HOST = "guardband-aae65.firebaseapp.com"
        const val PATH = "/__/auth/action"
        const val CODE = "oob-code-1234"
    }
}
