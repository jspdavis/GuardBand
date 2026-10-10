package com.example.guardband.data.repository

/**
 * A typed authentication failure, carried by [Result.failure].
 *
 * Carries no message on purpose: the wording belongs to the screen, so each
 * ViewModel maps these to its own `MSG_*` constants. [ContactError] follows the
 * same rule. [AlertRepository] is the one left that still puts user-facing text
 * in the exception message.
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

    /**
     * The password-reset link has expired.
     *
     * Firebase's own expiry, not the app's: reset codes are short-lived by
     * design. The only way forward is a new email, so the screen offers that
     * rather than a Retry.
     */
    object ExpiredActionCode : AuthError()

    /**
     * The password-reset code is not usable: already spent, revoked by a later
     * reset request, or simply wrong.
     *
     * Kept separate from [ExpiredActionCode] only because "it expired" and
     * "that link has already been used" are different things to be told. Both
     * end the same way, with a fresh request.
     */
    object InvalidActionCode : AuthError()
    object UserDisabled : AuthError()
    object Network : AuthError()
    object TooManyRequests : AuthError()

    /** The call needed a signed-in user and there was none. */
    object NotSignedIn : AuthError()

    /**
     * The account needs a more recent login before this operation is allowed.
     *
     * Firebase refuses `delete()` and a password change on a session that has
     * been sitting around, however valid it is. Not a credential failure and
     * not a dead session: the way out is to re-authenticate and try again,
     * which is why Delete account asks for the password or a fresh Google
     * token before it touches anything.
     */
    object RequiresRecentLogin : AuthError()

    /**
     * The credential offered belongs to a different account than the one
     * signed in.
     *
     * Reachable on re-authentication, not on sign-in. The Google chooser is
     * built with `setFilterByAuthorizedAccounts(false)` so it offers every
     * account on the device, which makes picking the wrong one easy; Firebase
     * answers `ERROR_USER_MISMATCH` rather than quietly signing in as somebody
     * else. The wording for it may say the account is different, because the
     * user just chose it themselves - nothing is being revealed.
     */
    object UserMismatch : AuthError()

    /**
     * A database read or write was rejected by the Security Rules.
     *
     * Only reachable from [UserProfileRepository], which shares this error
     * type. Distinct from [NotSignedIn]: the session is valid, the rules just
     * said no - which is the failure to expect while the `users/{uid}` rules
     * sit undeployed.
     */
    object PermissionDenied : AuthError()

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
