package com.example.guardband.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GoogleIdTokenFailure] - the part of the Google chooser's failure handling
 * that can be tested.
 *
 * `GoogleIdTokenProvider` itself needs a real Credential Manager, so the
 * decision was extracted here; these are the cases that used to all collapse
 * into "No Google account on this device."
 *
 * Plain JUnit: the classifier touches no Android API, so it needs neither
 * Robolectric nor a dispatcher rule.
 *
 * The messages below are the real wording from Play Services via
 * `credentials-play-services-auth` 1.3.0.
 */
class GoogleIdTokenFailureTest {

    private val notConfigured =
        "During begin sign in, failure response from one tap: 10: " +
            "Developer console is not set up correctly."

    private val blocked =
        "During begin sign in, failure response from one tap: 16: Caller has been " +
            "temporarily blocked due to too many canceled sign-in prompts."

    private val noMatching =
        "During begin sign in, failure response from one tap: 16: " +
            "Cannot find a matching credential."

    // ── classify ──────────────────────────────────────────────────────────────

    @Test
    fun `a status 10 failure is a configuration problem, not a missing account`() {
        assertEquals(
            GoogleIdTokenResult.NotConfigured,
            GoogleIdTokenFailure.classify(notConfigured, GoogleIdTokenResult.NoGoogleAccount)
        )
    }

    @Test
    fun `the developer-console wording is enough on its own`() {
        val noStatus = "Developer console is not set up correctly."

        assertEquals(
            GoogleIdTokenResult.NotConfigured,
            GoogleIdTokenFailure.classify(noStatus, GoogleIdTokenResult.NoGoogleAccount)
        )
    }

    @Test
    fun `a blocked caller is told apart from a missing account`() {
        assertEquals(
            GoogleIdTokenResult.TemporarilyBlocked,
            GoogleIdTokenFailure.classify(blocked, GoogleIdTokenResult.NoGoogleAccount)
        )
    }

    /**
     * Play Services uses 16 for the block *and* for "cannot find a matching
     * credential", so the number alone must not decide. This is the case that
     * breaks if someone adds 16 to the status check.
     */
    @Test
    fun `status 16 alone does not mean blocked`() {
        assertEquals(
            GoogleIdTokenResult.NoGoogleAccount,
            GoogleIdTokenFailure.classify(noMatching, GoogleIdTokenResult.NoGoogleAccount)
        )
    }

    @Test
    fun `an unrecognised message keeps the caller's fallback`() {
        assertEquals(
            GoogleIdTokenResult.NoGoogleAccount,
            GoogleIdTokenFailure.classify("something new", GoogleIdTokenResult.NoGoogleAccount)
        )
        assertEquals(
            GoogleIdTokenResult.Unknown,
            GoogleIdTokenFailure.classify("something new", GoogleIdTokenResult.Unknown)
        )
    }

    @Test
    fun `a null or blank message keeps the caller's fallback`() {
        assertEquals(
            GoogleIdTokenResult.Unknown,
            GoogleIdTokenFailure.classify(null, GoogleIdTokenResult.Unknown)
        )
        assertEquals(
            GoogleIdTokenResult.Unknown,
            GoogleIdTokenFailure.classify("", GoogleIdTokenResult.Unknown)
        )
    }

    @Test
    fun `the wording is matched whatever its case`() {
        assertEquals(
            GoogleIdTokenResult.NotConfigured,
            GoogleIdTokenFailure.classify(
                notConfigured.uppercase(),
                GoogleIdTokenResult.NoGoogleAccount
            )
        )
    }

    // ── statusCode ────────────────────────────────────────────────────────────

    @Test
    fun `the status code is read from between the colons`() {
        assertEquals(10, GoogleIdTokenFailure.statusCode(notConfigured))
        assertEquals(16, GoogleIdTokenFailure.statusCode(blocked))
    }

    @Test
    fun `a number in prose is not a status code`() {
        assertNull(GoogleIdTokenFailure.statusCode("error 10 occurred"))
        assertNull(GoogleIdTokenFailure.statusCode("no credentials available"))
        assertNull(GoogleIdTokenFailure.statusCode(null))
    }

    // ── diagnostic ────────────────────────────────────────────────────────────

    @Test
    fun `each cause gets its own diagnostic token`() {
        assertEquals(
            GoogleIdTokenFailure.DIAG_NOT_CONFIGURED,
            GoogleIdTokenFailure.diagnostic(notConfigured)
        )
        assertEquals(GoogleIdTokenFailure.DIAG_BLOCKED, GoogleIdTokenFailure.diagnostic(blocked))
        assertEquals(
            GoogleIdTokenFailure.DIAG_NO_CREDENTIAL,
            GoogleIdTokenFailure.diagnostic(noMatching)
        )
        assertEquals(
            GoogleIdTokenFailure.DIAG_UNCLASSIFIED,
            GoogleIdTokenFailure.diagnostic("something new")
        )
        assertEquals(GoogleIdTokenFailure.DIAG_UNCLASSIFIED, GoogleIdTokenFailure.diagnostic(null))
    }

    @Test
    fun `the diagnostic is always one of the allow-listed tokens`() {
        val allowed = setOf(
            GoogleIdTokenFailure.DIAG_NOT_CONFIGURED,
            GoogleIdTokenFailure.DIAG_BLOCKED,
            GoogleIdTokenFailure.DIAG_NO_CREDENTIAL,
            GoogleIdTokenFailure.DIAG_UNCLASSIFIED
        )
        val messages = listOf(
            notConfigured, blocked, noMatching, "something new", "", null,
            "No credentials available", "12:30:45 odd timestamp"
        )

        messages.forEach {
            assertTrue(
                "Unexpected diagnostic for: $it",
                GoogleIdTokenFailure.diagnostic(it) in allowed
            )
        }
    }

    /**
     * The rule the whole extraction exists to keep: a provider message can name
     * an account, so nothing derived from it may reach a log.
     */
    @Test
    fun `the diagnostic repeats nothing from the message`() {
        val withPii =
            "During begin sign in, failure response from one tap: 16: no credentials " +
                "available for alex.rivera@gmail.com on Alex's Pixel."

        val diagnostic = GoogleIdTokenFailure.diagnostic(withPii)

        assertFalse(diagnostic.contains("@"))
        assertFalse(diagnostic.contains("alex", ignoreCase = true))
        assertFalse(diagnostic.contains("rivera", ignoreCase = true))
        assertFalse(diagnostic.contains("pixel", ignoreCase = true))
        assertEquals(GoogleIdTokenFailure.DIAG_NO_CREDENTIAL, diagnostic)
    }
}
