package com.example.guardband.data.repository

/**
 * A typed authentication failure, carried by [Result.failure].
 *
 * Carries no message on purpose: the wording belongs to the screen, so each
 * ViewModel maps these to its own `MSG_*` constants. That is the one place the
 * data layer differs from [ContactRepository] and [AlertRepository], which
 * still put the user-facing text in the exception message.
 *
 * Extends [Exception] only so `Result.failure` accepts it.
 */
sealed class AuthError : Exception() {

    /**
     * The email/password pair was rejected.
     *
     * Also covers an unknown email: with Firebase's email-enumeration
     * protection on (the default for new projects) a wrong password and a
     * non-existent account both come back as `ERROR_INVALID_CREDENTIAL`.
     * Login must therefore not tell the two apart in its message.
     */
    object InvalidCredentials : AuthError()

    /** No account for that email. Firebase only reports this with enumeration protection off. */
    object NoSuchUser : AuthError()

    object EmailAlreadyInUse : AuthError()

    /**
     * The email already has an account created with a different provider, so
     * the credential just offered cannot sign in to it.
     *
     * Kept separate from [EmailAlreadyInUse] because the two need different
     * wording: this one is a returning user picking the wrong button, not
     * someone registering an address that is taken. The message for it must
     * stay neutral and must not name the other provider.
     */
    object AccountExistsWithDifferentCredential : AuthError()

    /** Rejected by Firebase's own minimum (6 characters); [InputValidator] asks for 8 first. */
    object WeakPassword : AuthError()

    object InvalidEmail : AuthError()
    object UserDisabled : AuthError()
    object Network : AuthError()
    object TooManyRequests : AuthError()

    /** The call needed a signed-in user and there was none. */
    object NotSignedIn : AuthError()

    /**
     * Anything unmapped. [code] is Firebase's `errorCode` when there was one;
     * it is never shown to the user and never logged.
     */
    data class Unknown(val code: String?) : AuthError()

    /**
     * The singletons above are shared instances, so a captured stack trace
     * would point at whichever call site happened to run first. Suppressing it
     * also keeps exception text out of logs entirely.
     */
    override fun fillInStackTrace(): Throwable = this
}
