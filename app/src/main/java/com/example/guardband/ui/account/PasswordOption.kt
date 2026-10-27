package com.example.guardband.ui.account

import com.example.guardband.data.model.SignInProvider

/**
 * What Account information's Password section shows. Pure, so the rule is a
 * plain JVM test.
 *
 * - **A password account** gets the Change password row. A mixed account is
 *   one too, because the password is still there to change - the same
 *   tie-break Delete account uses.
 * - **A Google account** gets an empty state instead of a row: its password
 *   is Google's, set in the Google account, and there is nothing here to
 *   change. Saying so beats a row that only answers with a toast.
 * - **An account with neither** has no section at all: there is nothing to
 *   offer and nothing true to say about it.
 */
enum class PasswordOption {
    CHANGE,
    MANAGED_BY_GOOGLE,
    NONE;

    companion object {
        fun of(providers: Set<SignInProvider>): PasswordOption = when {
            SignInProvider.PASSWORD in providers -> CHANGE
            SignInProvider.GOOGLE in providers -> MANAGED_BY_GOOGLE
            else -> NONE
        }
    }
}
