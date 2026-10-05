package com.example.guardband.ui.signup

/** Render state for [SignUpContactsActivity]. */
data class SignUpContactsUiState(
    /** True while registration is in flight: button disabled, progress visible. */
    val isLoading: Boolean = false,

    /**
     * True when a Google sign-in already created the account.
     *
     * The email and password fields are hidden and never validated: there is
     * no account to create, only a profile to finish.
     */
    val completeProfile: Boolean = false
)
