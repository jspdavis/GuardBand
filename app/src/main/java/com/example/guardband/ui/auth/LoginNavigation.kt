package com.example.guardband.ui.auth

import android.content.Context
import android.content.Intent
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.guardband.ui.login.LoginActivity
import com.example.guardband.ui.notifications.AlertWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     *
     * The alert watch stops first: nothing keeps watching the band for a
     * session that has ended. The switch itself stays as it was, so it
     * resumes on the next sign-in on this phone.
     *
     * Coil's caches are emptied too (see [clearImageCaches]), so the Google
     * photo does not stay on the phone after the session that showed it.
     */
    suspend fun clearCredentialsThenGoToLogin(
        context: Context,
        googleIdTokenProvider: GoogleIdTokenProvider
    ) {
        AlertWatch.stop(context)
        withTimeoutOrNull(CLEAR_CREDENTIALS_TIMEOUT_MS) {
            googleIdTokenProvider.clearCredentialState()
        }
        clearImageCaches(context)
        context.startActivity(
            Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    /**
     * Empties Coil's memory and disk caches. The only images Coil loads are
     * the Google profile photo (Profile's avatar and Track's band marker), so
     * this removes the signed-out account's photo and nothing else. Map tiles
     * are osmdroid's cache, not Coil's, and are left alone.
     *
     * The disk clear deletes files, so it runs on [Dispatchers.IO]. A failure
     * is ignored for the same reason as the credential clear: sign-out has
     * already happened, and a leftover file is not worth keeping anyone here.
     */
    @OptIn(ExperimentalCoilApi::class)
    private suspend fun clearImageCaches(context: Context) {
        val imageLoader = context.imageLoader
        imageLoader.memoryCache?.clear()
        withContext(Dispatchers.IO) {
            runCatching { imageLoader.diskCache?.clear() }
        }
    }
}
