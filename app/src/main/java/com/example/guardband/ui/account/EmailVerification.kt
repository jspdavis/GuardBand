package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider

/**
 * What the app says about the account's email, whether it offers Resend, and
 * whether the account is locked out until it verifies. Pure, so the rule is a
 * plain JVM test.
 *
 * - **Google accounts are verified**, whatever the flag says: Google already
 *   proved the address, and the user has no email to open. They never see
 *   Resend and are never gated.
 * - **Password accounts** are verified once Firebase says so. Until then they
 *   are offered Resend and **gated**: the Home host sends them to
 *   [VerifyRequiredActivity][com.example.guardband.ui.verify.VerifyRequiredActivity]
 *   instead of the tabs.
 * - An account with neither provider reads as not verified, with **no** Resend
 *   and **no** gate, because there is no flow this app could send it through;
 *   locking it out would strand it.
 */
data class EmailVerification(
    val verified: Boolean,
    val canResend: Boolean
) {
    /**
     * True when the app must not be used until the address is verified.
     * Exactly the accounts that can do something about it.
     */
    val requiredBeforeUse: Boolean get() = canResend

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
