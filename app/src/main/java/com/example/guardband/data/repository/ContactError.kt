package com.example.guardband.data.repository

/**
 * A typed emergency-contact failure, carried by [Result.failure].
 *
 * Carries no message, for the same reason [AuthError] does not: the wording
 * belongs to the screen, so each ViewModel maps these to its own `MSG_*`
 * constants. (Before this existed, the contact repository put user-facing text
 * in the exception message and the Contacts tab read `error.message` — which
 * meant a Firebase string could reach a Toast.)
 *
 * Extends [Exception] only so `Result.failure` accepts it.
 */
sealed class ContactError : Exception() {

    /** No signed-in user, so there is no `users/{uid}` to read or write. */
    object NotSignedIn : ContactError()

    /** The contact id is not under this user. Also covers a concurrent delete. */
    object NotFound : ContactError()

    /**
     * The name, phone or relationship failed [com.example.guardband.utils.InputValidator].
     *
     * The repository checks even though the dialog does: the repository is the
     * only layer every caller goes through, and an E.164 phone is a storage
     * guarantee rather than a presentation one.
     */
    object Validation : ContactError()

    /**
     * Deleting would leave fewer than
     * [MIN_CONTACTS][com.example.guardband.utils.InputValidator.MIN_CONTACTS].
     */
    object MinimumContacts : ContactError()

    /**
     * Already at [MAX_CONTACTS][com.example.guardband.utils.InputValidator.MAX_CONTACTS].
     */
    object MaximumContacts : ContactError()

    /** Rejected by the Security Rules. Distinct from [NotSignedIn]: the session is fine. */
    object PermissionDenied : ContactError()

    object Network : ContactError()

    /**
     * Anything unmapped. [code] is the Realtime Database's numeric error code
     * when there was one; it is never shown to the user and never logged.
     */
    data class Unknown(val code: Int?) : ContactError()

    /** Suppressed for the same reason as [AuthError.fillInStackTrace]. */
    override fun fillInStackTrace(): Throwable = this
}
