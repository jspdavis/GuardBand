package com.example.guardband.ui.auth

/**
 * The outcome of asking Credential Manager for a Google ID token.
 *
 * Typed rather than a nullable token, so a cancel can be told apart from a
 * failure: the screen must stay silent when the user dismisses the sheet, and
 * say something when the attempt actually broke.
 *
 * Only [Success] leaves the UI layer, and only as its [idToken] string — no
 * Credential Manager type reaches a ViewModel or a repository.
 */
sealed interface GoogleIdTokenResult {

    /** The token to hand to the auth repository. Never logged. */
    data class Success(val idToken: String) : GoogleIdTokenResult

    /** The user dismissed the sheet. Show nothing at all. */
    object Cancelled : GoogleIdTokenResult

    /** The device has no Google account, or none the user would share. */
    object NoGoogleAccount : GoogleIdTokenResult

    /** No Credential Manager provider: missing or outdated Play Services. */
    object Unavailable : GoogleIdTokenResult

    object Network : GoogleIdTokenResult

    /**
     * Anything else. Carries nothing: the underlying message could name an
     * account, so it is neither shown nor logged.
     */
    object Unknown : GoogleIdTokenResult
}
