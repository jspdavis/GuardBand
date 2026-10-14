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
    val email: String = ""
)
