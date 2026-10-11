package com.example.guardband.data.repository

/**
 * A typed alert-read failure, carried by [Result.failure].
 *
 * Carries no message, for the same reason [ContactError] and [AuthError] do
 * not: the wording belongs to the screen, so [AlertViewModel][com.example.guardband.ui.alert.AlertViewModel]
 * maps these to its own `MSG_*` constants. The Alert tab used to Toast
 * `error.message`, which is exactly how localised Firebase prose reaches a
 * user.
 *
 * Extends [Exception] only so `Result.failure` accepts it.
 */
sealed class AlertError : Exception() {

    /** No connection, or the read never reached the server. */
    object Network : AlertError()

    /** Rejected by the Security Rules. The session itself is fine. */
    object PermissionDenied : AlertError()

    /** The token is missing or dead, so the rules cannot identify the reader. */
    object NotSignedIn : AlertError()

    /**
     * The node was readable but its contents are not an alert.
     *
     * Only `latest` can fail this way. A malformed *history* entry is skipped
     * and counted (see [AlertHistory.malformedCount]) rather than failing the
     * whole list, because one bad row written by a firmware bug must not hide
     * every good row behind it.
     */
    object ParseFailure : AlertError()

    /**
     * Anything unmapped. [code] is the Realtime Database's numeric error code
     * when there was one; it is never shown to the user and never logged.
     */
    data class Unknown(val code: Int?) : AlertError()

    /** Suppressed for the same reason as [ContactError.fillInStackTrace]. */
    override fun fillInStackTrace(): Throwable = this
}
