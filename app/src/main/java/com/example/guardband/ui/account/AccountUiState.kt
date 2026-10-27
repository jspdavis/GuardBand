package com.example.guardband.ui.account

/**
 * Render state for [AccountActivity]'s details card, owned by
 * [AccountViewModel]. Delete account has its own state,
 * [DeleteAccountUiState].
 */
data class AccountUiState(
    /** The session's display name; blank when the account has none. */
    val name: String = "",

    /** The address the account signs in with. */
    val email: String = "",

    /** "Verified" or "Not verified" - see [EmailVerification]. */
    val emailVerified: Boolean = false,

    /** True for a password account that is not verified yet. */
    val canResend: Boolean = false,

    /** A verification email is being requested. */
    val isSending: Boolean = false,

    /** Seconds until Resend unlocks again; 0 when it is available. */
    val resendSecondsRemaining: Int = 0,

    /** The Password section: Change password, Google's empty state, or none. */
    val password: PasswordOption = PasswordOption.NONE
) {
    /** Whether the Resend button is shown at all. */
    val showResend: Boolean get() = canResend

    /** Whether it can be tapped right now. */
    val resendEnabled: Boolean
        get() = canResend && !isSending && resendSecondsRemaining == 0
}
