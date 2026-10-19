package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Account information says about the email, and whether it offers Resend. */
class EmailVerificationTest {

    @Test
    fun `a verified password account shows verified, no resend`() {
        assertEquals(
            EmailVerification(verified = true, canResend = false),
            EmailVerification.of(setOf(SignInProvider.PASSWORD), isEmailVerified = true)
        )
    }

    @Test
    fun `an unverified password account is offered resend`() {
        assertEquals(
            EmailVerification(verified = false, canResend = true),
            EmailVerification.of(setOf(SignInProvider.PASSWORD), isEmailVerified = false)
        )
    }

    /** Google proved the address; there is no inbox step to send them through. */
    @Test
    fun `a google account is verified even if the flag says otherwise`() {
        assertEquals(
            EmailVerification(verified = true, canResend = false),
            EmailVerification.of(setOf(SignInProvider.GOOGLE), isEmailVerified = false)
        )
    }

    @Test
    fun `a mixed account is verified through google and never sees resend`() {
        assertEquals(
            EmailVerification(verified = true, canResend = false),
            EmailVerification.of(
                setOf(SignInProvider.PASSWORD, SignInProvider.GOOGLE),
                isEmailVerified = false
            )
        )
    }

    @Test
    fun `an account with no known provider is not verified and has no resend`() {
        assertEquals(
            EmailVerification(verified = false, canResend = false),
            EmailVerification.of(emptySet(), isEmailVerified = false)
        )
    }

    // ── Which accounts are gated ──────────────────────────────────────────────

    @Test
    fun `only an unverified password account is gated`() {
        assertTrue(EmailVerification.of(setOf(SignInProvider.PASSWORD), false).requiredBeforeUse)
        assertFalse(EmailVerification.of(setOf(SignInProvider.PASSWORD), true).requiredBeforeUse)
        assertFalse(EmailVerification.of(setOf(SignInProvider.GOOGLE), false).requiredBeforeUse)
        assertFalse(
            EmailVerification.of(
                setOf(SignInProvider.PASSWORD, SignInProvider.GOOGLE),
                false
            ).requiredBeforeUse
        )
        assertFalse(EmailVerification.of(emptySet(), false).requiredBeforeUse)
    }
}
