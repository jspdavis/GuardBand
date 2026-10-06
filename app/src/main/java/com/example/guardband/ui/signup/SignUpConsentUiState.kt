package com.example.guardband.ui.signup

/** Render state for [SignUpConsentActivity]. */
data class SignUpConsentUiState(
    /** True while a repository call is in flight: buttons disabled, progress visible. */
    val isLoading: Boolean = false,

    /** The checkbox. Create account stays disabled until this is true. */
    val consentGiven: Boolean = false,

    /**
     * True once the Firebase account exists.
     *
     * The guard that stops a second submit from calling `createAccount` again,
     * which would fail as "email already in use" and strand the address — see
     * [SignUpConsentViewModel].
     */
    val accountCreated: Boolean = false,

    /**
     * True when the account is made but the record write failed, so the screen
     * shows Retry.
     *
     * Retry re-runs only the write. There is nothing to undo first, because
     * that write is atomic.
     */
    val canRetry: Boolean = false
)
