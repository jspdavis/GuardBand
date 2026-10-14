package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider

/**
 * What Account information says about the account's email, and whether it
 * offers Resend. Pure, so the rule is a plain JVM test.
 *
 * - **Google accounts are verified**, whatever the flag says: Google already
 *   proved the address, and the user has no email to open. They never see
 *   Resend.
 * - **Password accounts** are verified once Firebase says so, and until then
 *   are offered Resend.
 * - An account with neither provider reads as not verified with **no** Resend,
 *   because there is no flow this app could send it through.
 *
 * Shown, never enforced: nothing in the app is gated on it.
 */
data class EmailVerification(
    val verified: Boolean,
    val canResend: Boolean
) {
    companion object {
        fun of(providers: Set<SignInProvider>, isEmailVerified: Boolean): EmailVerification {
            val verified = isEmailVerified || SignInProvider.GOOGLE in providers
            return EmailVerification(
                verified = verified,
                canResend = !verified && SignInProvider.PASSWORD in providers
            )
        }
    }
}
