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

    /**
     * The device has no Google account to offer.
     *
     * Only that. The two other causes Credential Manager reports as the same
     * exception have their own cases below, and [GoogleIdTokenFailure] tells
     * the three apart.
     */
    object NoGoogleAccount : GoogleIdTokenResult

    /**
     * This build is not authorised for the Firebase project: its signing
     * certificate's SHA-1 is not registered, or the OAuth client is wrong.
     *
     * A developer-machine or release-keystore problem, never the user's. Every
     * developer registers their own debug SHA-1 (CLAUDE.md, "Google sign-in");
     * an unregistered one fails here even though the app builds fine.
     */
    object NotConfigured : GoogleIdTokenResult

    /**
     * Play Services has paused sign-in prompts for this caller after too many
     * dismissed sheets. It clears itself after a while.
     */
    object TemporarilyBlocked : GoogleIdTokenResult

    /** No Credential Manager provider: missing or outdated Play Services. */
    object Unavailable : GoogleIdTokenResult

    object Network : GoogleIdTokenResult

    /**
     * Anything else. Carries nothing: the underlying message could name an
     * account, so it is neither shown nor logged.
     */
    object Unknown : GoogleIdTokenResult
}
