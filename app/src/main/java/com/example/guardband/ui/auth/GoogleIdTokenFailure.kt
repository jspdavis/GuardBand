package com.example.guardband.ui.auth

/**
 * Tells apart the causes that Credential Manager reports as one exception.
 *
 * `NoCredentialException` means "no credential was offered", which covers at
 * least three unrelated situations:
 *
 * 1. the phone genuinely has no Google account,
 * 2. **this build is not authorised for the Firebase project** - its signing
 *    certificate's SHA-1 is not registered, which Play Services reports as
 *    status [STATUS_DEVELOPER_ERROR],
 * 3. Play Services has paused prompts for this caller after repeated
 *    dismissals.
 *
 * Reporting all three as "no Google account on this device" told the user to
 * add an account they already had, and two of the three are not theirs to fix.
 *
 * **Pure on purpose.** [GoogleIdTokenProvider] cannot be unit-tested - it
 * needs a real Credential Manager, and the project declares no mocking library
 * - so the decision lives here, where a plain JVM test can reach it. The same
 * split as `ProfileProvisioning` and `BandList`.
 *
 * **Nothing here keeps the message.** Both functions reduce it to a typed
 * result or one of the four `DIAG_` tokens below, and neither returns a slice
 * of its input. That is what lets a debug build log the outcome without
 * breaking the rule that a provider message - which can name an account -
 * never reaches a log. Same discipline as `FirebaseAuthErrorMapper`, which
 * maps error codes and never messages.
 */
internal object GoogleIdTokenFailure {

    /** `CommonStatusCodes.DEVELOPER_ERROR`: the caller is not authorised. */
    const val STATUS_DEVELOPER_ERROR = 10

    const val DIAG_NOT_CONFIGURED = "signing-cert-or-oauth-client-not-registered"
    const val DIAG_BLOCKED = "provider-temporarily-blocked"
    const val DIAG_NO_CREDENTIAL = "no-credential-offered"
    const val DIAG_UNCLASSIFIED = "unclassified"

    /**
     * Wording from Play Services, through `credentials-play-services-auth`
     * 1.3.0. Re-check these when that dependency moves: the library is pinned
     * by `compileSdk 34` today, so the text is stable, but it is not API.
     */
    private const val MARKER_DEVELOPER_CONSOLE = "developer console is not set up correctly"
    private const val MARKER_BLOCKED = "temporarily blocked"
    private const val MARKER_NO_MATCHING = "cannot find a matching credential"
    private const val MARKER_NONE_AVAILABLE = "no credentials available"

    /** The `: <code>:` that One Tap wraps its status in. */
    private val STATUS = Regex(""":\s*(\d{1,3}):""")

    /**
     * Which failure this was, or [fallback] when the message says nothing
     * recognisable.
     *
     * Unrecognised shapes keep the caller's existing behaviour rather than
     * guessing, so a library upgrade that reworded something can only cost the
     * sharper message - never produce a confident wrong one.
     *
     * **Status 16 is matched on wording, not on the code**: Play Services uses
     * it both for "cannot find a matching credential" and for the temporary
     * block, so the number alone cannot separate them. Only
     * [STATUS_DEVELOPER_ERROR] is unambiguous.
     */
    fun classify(message: String?, fallback: GoogleIdTokenResult): GoogleIdTokenResult {
        val text = message?.lowercase() ?: return fallback
        return when {
            statusCode(message) == STATUS_DEVELOPER_ERROR ||
                text.contains(MARKER_DEVELOPER_CONSOLE) -> GoogleIdTokenResult.NotConfigured

            text.contains(MARKER_BLOCKED) -> GoogleIdTokenResult.TemporarilyBlocked

            else -> fallback
        }
    }

    /**
     * One allow-listed token naming the cause, safe to log.
     *
     * Always one of the four `DIAG_` constants - never anything derived from
     * [message].
     */
    fun diagnostic(message: String?): String {
        val text = message?.lowercase() ?: return DIAG_UNCLASSIFIED
        return when {
            statusCode(message) == STATUS_DEVELOPER_ERROR ||
                text.contains(MARKER_DEVELOPER_CONSOLE) -> DIAG_NOT_CONFIGURED

            text.contains(MARKER_BLOCKED) -> DIAG_BLOCKED

            text.contains(MARKER_NO_MATCHING) ||
                text.contains(MARKER_NONE_AVAILABLE) -> DIAG_NO_CREDENTIAL

            else -> DIAG_UNCLASSIFIED
        }
    }

    /**
     * The Play Services status code the message carries, or `null`.
     *
     * Returns a number only where one sits between colons, as One Tap formats
     * it; a bare number in prose is not a status code.
     */
    fun statusCode(message: String?): Int? =
        message?.let { STATUS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
}
