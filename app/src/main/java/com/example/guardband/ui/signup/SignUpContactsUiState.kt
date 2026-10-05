package com.example.guardband.ui.signup

/** Render state for [SignUpContactsActivity]. */
data class SignUpContactsUiState(
    /** True while a repository call is in flight: button disabled, progress visible. */
    val isLoading: Boolean = false,

    /**
     * True when a Google sign-in already created the account.
     *
     * The email and password fields are hidden and never validated: there is
     * no account to create, only a profile to finish.
     */
    val completeProfile: Boolean = false,

    /**
     * True once the Firebase account exists.
     *
     * The guard that stops a second submit from calling `createAccount` again,
     * which would fail as "email already in use" and strand the address - see
     * [SignUpContactsViewModel].
     */
    val accountCreated: Boolean = false,

    /**
     * True when the account is made but its profile write failed, so the
     * screen shows Retry.
     *
     * Retry re-runs only the write. There is nothing to undo first, because
     * that write is atomic.
     */
    val canRetry: Boolean = false
)
