package com.example.guardband.ui.auth

import android.content.Context
import android.content.Intent
import com.example.guardband.ui.login.LoginActivity
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one way a signed-in screen leaves for Login: Log Out in Settings, and a
 * finished Delete account in Account information. One place, so the two
 * cannot drift apart.
 */
object LoginNavigation {

    /** How long the Credential Manager clear may hold up the way out. */
    const val CLEAR_CREDENTIALS_TIMEOUT_MS = 2_000L

    /**
     * Forgets the stored Google account, then goes to Login with the back
     * stack cleared.
     *
     * The clear is bounded by [CLEAR_CREDENTIALS_TIMEOUT_MS] and its failures
     * are swallowed inside the provider: the user has already been signed out
     * of Firebase by this point - and after a delete the account does not
     * exist at all - so nothing here may keep them on the screen. Worst case
     * the chooser remembers the account, which is a nuisance, not a lock-in.
     */
    suspend fun clearCredentialsThenGoToLogin(
        context: Context,
        googleIdTokenProvider: GoogleIdTokenProvider
    ) {
        withTimeoutOrNull(CLEAR_CREDENTIALS_TIMEOUT_MS) {
            googleIdTokenProvider.clearCredentialState()
        }
        context.startActivity(
            Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
