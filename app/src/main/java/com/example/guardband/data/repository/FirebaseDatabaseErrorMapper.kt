package com.example.guardband.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DatabaseError

/**
 * Turns Realtime Database failures into [ContactError] or [AuthError].
 *
 * Maps on [DatabaseError.getCode], never on the exception message, for the same
 * reason [FirebaseAuthErrorMapper] does: messages are localised prose and
 * change between SDK versions.
 *
 * That numeric code is **only** reachable through the SDK's completion-listener
 * and [com.google.firebase.database.ValueEventListener] callbacks. A
 * `Task`-based call awaited with `kotlinx-coroutines-play-services` fails with a
 * bare `DatabaseException` whose only payload is that prose. So the repositories
 * here wrap the listener forms in `suspendCancellableCoroutine` rather than
 * calling `.await()` — more code, but it is what makes mapping by code possible
 * at all.
 *
 * Pure and Android-free, so both `fromCode` functions are unit-testable without
 * a device.
 */
internal object FirebaseDatabaseErrorMapper {

    // ── Contacts ──────────────────────────────────────────────────────────────

    /** Maps one [DatabaseError] from a contact read or write. */
    fun map(error: DatabaseError): ContactError = fromCode(error.code)

    /** Maps anything thrown by a contact call. [ContactError]s pass straight through. */
    fun map(throwable: Throwable): ContactError = when (throwable) {
        is ContactError -> throwable
        is FirebaseNetworkException -> ContactError.Network
        else -> ContactError.Unknown(null)
    }

    /** Maps one [DatabaseError.getCode]. Unrecognised codes become [ContactError.Unknown]. */
    fun fromCode(code: Int): ContactError = when (code) {
        DatabaseError.PERMISSION_DENIED -> ContactError.PermissionDenied

        // All three mean "the write never reached the server", which is the
        // same thing to a user holding a phone with no signal.
        DatabaseError.NETWORK_ERROR,
        DatabaseError.DISCONNECTED,
        DatabaseError.UNAVAILABLE -> ContactError.Network

        // The rules evaluate auth != null, so a dead token reads as "not you"
        // rather than as a permission problem with the data.
        DatabaseError.EXPIRED_TOKEN,
        DatabaseError.INVALID_TOKEN -> ContactError.NotSignedIn

        else -> ContactError.Unknown(code)
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    /**
     * The same mapping in [AlertError]'s vocabulary, for [AlertRepository].
     *
     * [AlertError.ParseFailure] is deliberately absent here: it describes the
     * payload, not the connection, so nothing in a [DatabaseError] can produce
     * it.
     */
    fun mapToAlertError(error: DatabaseError): AlertError = fromCodeToAlert(error.code)

    /**
     * Maps one [DatabaseError.getCode]. Unrecognised codes become
     * [AlertError.Unknown].
     *
     * Split out of [mapToAlertError] for the same reason [fromCode] is: a
     * [DatabaseError] cannot be constructed in a JVM test, so the mapping is
     * tested through the code instead.
     */
    fun fromCodeToAlert(code: Int): AlertError = when (code) {
        DatabaseError.PERMISSION_DENIED -> AlertError.PermissionDenied

        DatabaseError.NETWORK_ERROR,
        DatabaseError.DISCONNECTED,
        DatabaseError.UNAVAILABLE -> AlertError.Network

        DatabaseError.EXPIRED_TOKEN,
        DatabaseError.INVALID_TOKEN -> AlertError.NotSignedIn

        else -> AlertError.Unknown(code)
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    /**
     * The same mapping in [AuthError]'s vocabulary, for
     * [UserProfileRepository], which shares its error type with
     * [AuthRepository] rather than having one of its own.
     */
    fun mapToAuthError(error: DatabaseError): AuthError = when (error.code) {
        DatabaseError.PERMISSION_DENIED -> AuthError.PermissionDenied

        DatabaseError.NETWORK_ERROR,
        DatabaseError.DISCONNECTED,
        DatabaseError.UNAVAILABLE -> AuthError.Network

        DatabaseError.EXPIRED_TOKEN,
        DatabaseError.INVALID_TOKEN -> AuthError.NotSignedIn

        else -> AuthError.Unknown(null)
    }
}
