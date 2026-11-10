package com.example.guardband.data.repository

import com.example.guardband.data.model.ConnectedWearer
import kotlinx.coroutines.flow.Flow

/**
 * Invite-code pairing between wearer and watcher accounts.
 *
 * A wearer generates a short code and shares it out-of-band; a watcher
 * enters the same code on their device. That one exchange writes the
 * bi-directional mapping at `wearer_watchers` and `watcher_wearers`.
 *
 * All paths are described in docs/device-pairing.md and the RTDB rules in
 * RTDBS-RULES.md. Nothing here logs a uid, a name or any other PII.
 */
interface WearerConnectionRepository {

    /**
     * Writes a new invite code to `invite_codes/{code}` and returns it.
     *
     * The code is 6 uppercase alphanumeric characters (e.g. "GB8X4Z"),
     * valid for one hour. The code may be reused by multiple watchers
     * within the window; [redeemInviteCode] writes both sides of the link
     * on each use.
     *
     * Fails with [WearerConnectionError.NotSignedIn] when no account is
     * signed in; [WearerConnectionError.Network] on a database error.
     */
    suspend fun generateInviteCode(): Result<String>

    /**
     * Validates [code] and writes the bi-directional mapping.
     *
     * Checks that the code exists, that [expires_at] is in the future,
     * that [used] is false, and that the signed-in account is not the
     * wearer themselves. On success writes:
     *   - `wearer_watchers/{wearer_uid}/{my_uid}` = true
     *   - `watcher_wearers/{my_uid}/{wearer_uid}` = true
     *
     * Fails with [WearerConnectionError.CodeNotFound] when the code does
     * not exist; [WearerConnectionError.CodeExpired] when it is past its
     * window; [WearerConnectionError.AlreadyLinked] when this account is
     * already the wearer; [WearerConnectionError.NotSignedIn] or
     * [WearerConnectionError.Network] otherwise.
     */
    suspend fun redeemInviteCode(code: String): Result<Unit>

    /**
     * Live list of wearers this watcher is monitoring, with their display
     * names and band ids. Used by the Track-tab switcher.
     *
     * Reads `watcher_wearers/{my_uid}`, then for each wearer uid reads
     * `users/{wearer_uid}/name` and `users/{wearer_uid}/bands/`. Emits an
     * empty list when no wearers are linked or no account is signed in.
     */
    fun observeConnectedWearers(): Flow<Result<List<ConnectedWearer>>>

    /**
     * Removes [watcherUid] from the wearer's list (the signed-in account
     * is the wearer). Deletes both sides of the link.
     */
    suspend fun removeWatcher(watcherUid: String): Result<Unit>

    /**
     * Stops watching [wearerUid] (the signed-in account is the watcher).
     * Deletes both sides of the link.
     */
    suspend fun stopWatching(wearerUid: String): Result<Unit>
}

/** Errors the connection repository surfaces. */
sealed class WearerConnectionError : Exception() {
    /** No Firebase Auth session. */
    object NotSignedIn : WearerConnectionError()

    /** The invite code was not found in `invite_codes/`. */
    object CodeNotFound : WearerConnectionError()

    /** The invite code exists but its window has passed. */
    object CodeExpired : WearerConnectionError()

    /** The signed-in account is the wearer who generated the code. */
    object AlreadyLinked : WearerConnectionError()

    /** Firebase Security Rules rejected the operation (e.g. unreleased node rules). */
    object PermissionDenied : WearerConnectionError()

    /** A Firebase database error (network, timeout, etc.). */
    class Network(override val cause: Throwable? = null) : WearerConnectionError()
}
