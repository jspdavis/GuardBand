package com.example.guardband.data.repository

import android.util.Log
import com.example.guardband.data.model.ConnectedWearer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [WearerConnectionRepository] on Firebase Realtime Database.
 *
 * Paths used (all described in RTDBS-RULES.md):
 *   invite_codes/{code}                        — invite records
 *   wearer_watchers/{wearer_uid}/{watcher_uid} — link (wearer's view)
 *   watcher_wearers/{watcher_uid}/{wearer_uid} — link (watcher's view)
 *   users/{uid}/name                           — display name (read-only here)
 *   users/{uid}/bands/                         — device ids (read-only here)
 *
 * Nothing here logs a uid, name or any personal data.
 */
class FirebaseWearerConnectionRepository(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : WearerConnectionRepository {

    // ── Code generation ───────────────────────────────────────────────────────

    override suspend fun generateInviteCode(): Result<String> = connectionResult {
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            val uid = requireUid()
            val code = randomCode()
            val now = System.currentTimeMillis()
            val record = mapOf(
                FIELD_WEARER_UID to uid,
                FIELD_CREATED_AT to ServerValue.TIMESTAMP,
                FIELD_EXPIRES_AT to (now + CODE_TTL_MS),
                FIELD_USED to false
            )
            database.reference.child(NODE_INVITE_CODES).child(code).setRecord(record)
            code
        } ?: throw WearerConnectionError.Network(null)
    }

    // ── Code redemption ───────────────────────────────────────────────────────

    override suspend fun redeemInviteCode(code: String): Result<Unit> = connectionResult {
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            val myUid = requireUid()
            val snapshot = database.reference
                .child(NODE_INVITE_CODES).child(code)
                .readOnce()

            if (!snapshot.exists()) throw WearerConnectionError.CodeNotFound
            val expiresAt = snapshot.child(FIELD_EXPIRES_AT).getValue(Long::class.java) ?: 0L
            if (System.currentTimeMillis() > expiresAt) throw WearerConnectionError.CodeExpired
            val wearerUid = snapshot.child(FIELD_WEARER_UID).value?.toString()
                ?: throw WearerConnectionError.CodeNotFound
            if (wearerUid == myUid) throw WearerConnectionError.AlreadyLinked

            // Bi-directional link, one atomic multi-path write.
            val updates = mapOf<String, Any>(
                "$NODE_WEARER_WATCHERS/$wearerUid/$myUid" to true,
                "$NODE_WATCHER_WEARERS/$myUid/$wearerUid" to true
            )
            database.reference.updatePaths(updates)
        } ?: throw WearerConnectionError.Network(null)
    }

    // ── Observe connected wearers ─────────────────────────────────────────────

    override fun observeConnectedWearers(): Flow<Result<List<ConnectedWearer>>> {
        val uid = auth.currentUser?.uid
            ?: return flowOf(Result.success(emptyList()))
        return callbackFlow {
            val ref = database.reference.child(NODE_WATCHER_WEARERS).child(uid)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val wearerUids = snapshot.children.mapNotNull { it.key }
                    if (wearerUids.isEmpty()) {
                        trySend(Result.success(emptyList()))
                        return
                    }
                    // callbackFlow's ProducerScope is a CoroutineScope.
                    launch {
                        val wearers = wearerUids.mapNotNull { wearerUid ->
                            runCatching { readConnectedWearer(wearerUid) }
                                .onFailure { e ->
                                    // Surface permission errors — if rules aren't
                                    // published the whole list will be empty with no hint.
                                    if (e is WearerConnectionError.PermissionDenied) {
                                        trySend(Result.failure(e))
                                    }
                                }
                                .getOrNull()
                        }
                        trySend(Result.success(wearers))
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(Result.failure(WearerConnectionError.Network(error.toException())))
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }
    }

    private suspend fun readConnectedWearer(wearerUid: String): ConnectedWearer {
        val userSnap = database.reference.child(NODE_USERS).child(wearerUid).readOnce()
        val name = userSnap.child(FIELD_NAME).value?.toString().orEmpty()
        val deviceId = userSnap.child(NODE_BANDS).children.firstOrNull()?.key ?: wearerUid
        return ConnectedWearer(wearerUid = wearerUid, displayName = name, deviceId = deviceId)
    }

    // ── Remove / stop watching ────────────────────────────────────────────────

    override suspend fun removeWatcher(watcherUid: String): Result<Unit> = connectionResult {
        val myUid = requireUid()
        val updates = mapOf<String, Any?>(
            "$NODE_WEARER_WATCHERS/$myUid/$watcherUid" to null,
            "$NODE_WATCHER_WEARERS/$watcherUid/$myUid" to null
        )
        database.reference.updatePaths(updates)
    }

    override suspend fun stopWatching(wearerUid: String): Result<Unit> = connectionResult {
        val myUid = requireUid()
        val updates = mapOf<String, Any?>(
            "$NODE_WEARER_WATCHERS/$wearerUid/$myUid" to null,
            "$NODE_WATCHER_WEARERS/$myUid/$wearerUid" to null
        )
        database.reference.updatePaths(updates)
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw WearerConnectionError.NotSignedIn

    private fun randomCode(): String {
        val chars = (('A'..'Z') + ('0'..'9')).toMutableList()
        chars.shuffle()
        return chars.take(CODE_LENGTH).joinToString("")
    }

    /** One-shot read, suspending, with the error mapped to [WearerConnectionError]. */
    private suspend fun DatabaseReference.readOnce(): DataSnapshot =
        suspendCancellableCoroutine { cont ->
            addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (cont.isActive) cont.resume(s)
                }

                override fun onCancelled(e: DatabaseError) {
                    if (cont.isActive) {
                        val ex = when (e.code) {
                            DatabaseError.PERMISSION_DENIED -> WearerConnectionError.PermissionDenied
                            else -> WearerConnectionError.Network(e.toException())
                        }
                        cont.resumeWithException(ex)
                    }
                }
            })
        }

    private suspend fun DatabaseReference.setRecord(record: Map<String, Any?>): Unit =
        suspendCancellableCoroutine { cont ->
            setValue(record) { error, _ ->
                if (!cont.isActive) return@setValue
                if (error == null) {
                    cont.resume(Unit)
                } else {
                    val ex = when (error.code) {
                        DatabaseError.PERMISSION_DENIED -> WearerConnectionError.PermissionDenied
                        else -> WearerConnectionError.Network(error.toException())
                    }
                    cont.resumeWithException(ex)
                }
            }
        }

    private suspend fun DatabaseReference.updatePaths(updates: Map<String, Any?>): Unit =
        suspendCancellableCoroutine { cont ->
            updateChildren(updates) { error, _ ->
                if (!cont.isActive) return@updateChildren
                if (error == null) {
                    cont.resume(Unit)
                } else {
                    val ex = when (error.code) {
                        DatabaseError.PERMISSION_DENIED -> WearerConnectionError.PermissionDenied
                        else -> WearerConnectionError.Network(error.toException())
                    }
                    cont.resumeWithException(ex)
                }
            }
        }

    private inline fun <T> connectionResult(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: WearerConnectionError) {
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("permission") || msg.contains("denied") || msg.contains("access")) {
                Result.failure(WearerConnectionError.PermissionDenied)
            } else {
                Result.failure(WearerConnectionError.Network(e))
            }
        }

    private companion object {
        const val NODE_INVITE_CODES    = "invite_codes"
        const val NODE_WEARER_WATCHERS = "wearer_watchers"
        const val NODE_WATCHER_WEARERS = "watcher_wearers"
        const val NODE_USERS           = "users"
        const val NODE_BANDS           = "bands"
        const val FIELD_WEARER_UID     = "wearer_uid"
        const val FIELD_CREATED_AT     = "created_at"
        const val FIELD_EXPIRES_AT     = "expires_at"
        const val FIELD_USED           = "used"
        const val FIELD_NAME           = "name"
        const val CODE_LENGTH          = 6
        const val CODE_TTL_MS          = 3_600_000L   // 1 hour
        const val WRITE_TIMEOUT_MS     = 10_000L      // 10 seconds
    }
}
