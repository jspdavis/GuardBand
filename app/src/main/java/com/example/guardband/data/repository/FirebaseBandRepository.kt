package com.example.guardband.data.repository

import com.example.guardband.data.model.PairedBand
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [BandRepository] backed by Firebase Realtime Database.
 *
 * Listens to the account's own claims (`users/{uid}/bands`) and to the
 * wearers it watches (`watcher_wearers/{uid}`), reads each wearer's claims
 * once (`users/{wearer}/bands`), and hands all of it to [BandList], which
 * decides the list (the demo band when nothing is claimed). It emits whenever
 * either listener changes.
 *
 * Every read degrades instead of stalling: a failed or refused read counts as
 * "nothing there", so the list still arrives (with the demo band).
 *
 * Returns an empty list when no account is signed in. Nothing here logs a uid
 * or a device id.
 */
class FirebaseBandRepository(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : BandRepository {

    override fun observeBands(): Flow<List<PairedBand>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val ownBandsRef = bandsRef(uid)
            val watcherWearersRef = database.reference.child(NODE_WATCHER_WEARERS).child(uid)

            // Null until each listener has answered once, so the first
            // emission never says "nothing claimed" before the read is in.
            var claimed: List<String>? = null
            var wearerUids: List<String>? = null
            var rebuildJob: Job? = null

            fun rebuild() {
                val own = claimed ?: return
                val wearers = wearerUids ?: return
                rebuildJob?.cancel()
                rebuildJob = launch {
                    val watched = wearers.flatMap { bandsRef(it).readKeysOrEmpty() }
                    send(BandList.of(own, watched))
                }
            }

            val ownListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    claimed = snapshot.children.mapNotNull { it.key }
                    rebuild()
                }

                override fun onCancelled(error: DatabaseError) {
                    claimed = emptyList()
                    rebuild()
                }
            }

            val watcherListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    wearerUids = snapshot.children.mapNotNull { it.key }
                    rebuild()
                }

                override fun onCancelled(error: DatabaseError) {
                    wearerUids = emptyList()
                    rebuild()
                }
            }

            ownBandsRef.addValueEventListener(ownListener)
            watcherWearersRef.addValueEventListener(watcherListener)
            awaitClose {
                ownBandsRef.removeEventListener(ownListener)
                watcherWearersRef.removeEventListener(watcherListener)
            }
        }
    }

    /**
     * One `setValue` of the whole `users/{uid}/bands` node, so a claim
     * replaces the band worn before. Owner-only under the existing
     * `users/$uid` rule; no rules change was needed.
     */
    override suspend fun claimBand(deviceId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(BandError.NotSignedIn)
        return withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                bandsRef(uid).setValue(mapOf(deviceId to RELATION_WEARER)) { error, _ ->
                    if (!cont.isActive) return@setValue
                    cont.resume(
                        if (error == null) Result.success(Unit) else Result.failure(bandErrorOf(error))
                    )
                }
            }
        } ?: Result.failure(BandError.Network)
    }

    private fun bandsRef(uid: String): DatabaseReference =
        database.reference.child(NODE_USERS).child(uid).child(NODE_BANDS)

    /** The node's keys, or an empty list when the read fails or is refused. */
    private suspend fun DatabaseReference.readKeysOrEmpty(): List<String> =
        suspendCancellableCoroutine { cont ->
            addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (cont.isActive) cont.resume(s.children.mapNotNull { it.key })
                }

                override fun onCancelled(e: DatabaseError) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            })
        }

    /** By code, never by message (CLAUDE.md, "Firebase"). */
    private fun bandErrorOf(error: DatabaseError): BandError = when (error.code) {
        DatabaseError.PERMISSION_DENIED -> BandError.PermissionDenied
        else -> BandError.Network
    }

    private companion object {
        const val NODE_WATCHER_WEARERS = "watcher_wearers"
        const val NODE_USERS = "users"
        const val NODE_BANDS = "bands"
        const val RELATION_WEARER = "wearer"
        const val WRITE_TIMEOUT_MS = 10_000L
    }
}
