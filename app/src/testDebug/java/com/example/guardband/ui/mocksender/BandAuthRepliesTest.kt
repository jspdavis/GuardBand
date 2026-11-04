package com.example.guardband.ui.mocksender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Reading the Auth REST API's replies for the Mock Sender's band identity:
 * the sign-up reply (camelCase), the token reply (snake_case), Firebase's
 * error codes and when to renew. The replies are shaped like Firebase's, with
 * made-up values.
 *
 * Robolectric, because `org.json` is only a stub on the plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
class BandAuthRepliesTest {

    @Test
    fun `a sign-up reply gives the band id and both tokens`() {
        val session = BandAuthReplies.fromSignUp(
            """{"kind":"identitytoolkit#SignupNewUserResponse","idToken":"id.tok.en",
               "refreshToken":"refresh-1","expiresIn":"3600","localId":"band-uid-1"}"""
        )

        assertEquals(BandAuthReplies.Session("band-uid-1", "id.tok.en", "refresh-1", 3_600L), session)
    }

    @Test
    fun `a sign-up reply missing any of them is refused`() {
        assertNull(BandAuthReplies.fromSignUp("""{"idToken":"t","refreshToken":"r","expiresIn":"3600"}"""))
        assertNull(BandAuthReplies.fromSignUp("""{"localId":"u","refreshToken":"r"}"""))
        assertNull(BandAuthReplies.fromSignUp("""{"localId":"u","idToken":"t"}"""))
        assertNull(BandAuthReplies.fromSignUp("not json"))
    }

    @Test
    fun `a token reply is snake_case, and may hand back a new refresh token`() {
        val session = BandAuthReplies.fromRefresh(
            """{"access_token":"id.tok.en","expires_in":"3600","token_type":"Bearer",
               "refresh_token":"refresh-2","id_token":"id.tok.en","user_id":"band-uid-1",
               "project_id":"123"}"""
        )

        assertEquals(BandAuthReplies.Session("band-uid-1", "id.tok.en", "refresh-2", 3_600L), session)
    }

    @Test
    fun `a token reply needs only the ID token`() {
        val session = BandAuthReplies.fromRefresh("""{"id_token":"id.tok.en"}""")

        assertEquals(BandAuthReplies.Session(null, "id.tok.en", null, 3_600L), session)
        assertNull(BandAuthReplies.fromRefresh("""{"refresh_token":"r"}"""))
    }

    @Test
    fun `an unreadable lifetime falls back to Firebase's hour`() {
        val session = BandAuthReplies.fromRefresh("""{"id_token":"t","expires_in":"soon"}""")

        assertEquals(3_600L, session?.expiresInSeconds)
    }

    @Test
    fun `the error code is Firebase's message`() {
        val body = """{"error":{"code":400,"message":"OPERATION_NOT_ALLOWED","errors":[]}}"""

        assertEquals("OPERATION_NOT_ALLOWED", BandAuthReplies.errorCode(body))
        assertNull(BandAuthReplies.errorCode("""{"error":"Permission denied"}"""))
        assertNull(BandAuthReplies.errorCode(""))
        assertNull(BandAuthReplies.errorCode(null))
    }

    @Test
    fun `anonymous sign-in being off, and a dead identity, each say what to do`() {
        assertEquals(
            "turn on Anonymous sign-in in the Firebase Console",
            BandAuthReplies.hintFor("OPERATION_NOT_ALLOWED")
        )
        assertEquals(
            "turn on Anonymous sign-in in the Firebase Console",
            BandAuthReplies.hintFor("ADMIN_ONLY_OPERATION")
        )
        for (code in listOf("TOKEN_EXPIRED", "USER_NOT_FOUND", "USER_DISABLED", "INVALID_REFRESH_TOKEN")) {
            assertEquals(code, "this band id no longer works: tap New band id", BandAuthReplies.hintFor(code))
        }
        assertNull(BandAuthReplies.hintFor("QUOTA_EXCEEDED"))
        assertNull(BandAuthReplies.hintFor(null))
    }

    @Test
    fun `a token is renewed five minutes before it runs out`() {
        val expiresAt = 10_000_000L

        assertFalse(BandAuthReplies.needsRefresh(expiresAt, now = expiresAt - 5 * 60_000L - 1))
        assertTrue(BandAuthReplies.needsRefresh(expiresAt, now = expiresAt - 5 * 60_000L))
        assertTrue(BandAuthReplies.needsRefresh(expiresAt, now = expiresAt + 1))
    }
}
