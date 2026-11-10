package com.example.guardband.data.repository

import com.example.guardband.data.DeviceConstants
import com.example.guardband.data.model.BandRelation
import com.example.guardband.data.model.PairedBand
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [BandRepository] backed by Firebase Realtime Database.
 *
 * Reads the signed-in account's own bands from `users/{uid}/bands/` and the
 * wearers they watch from `watcher_wearers/{uid}`, then each wearer's bands
 * from `users/{wearer_uid}/bands/`. Combines both sides into one
 * `List<PairedBand>` and emits it whenever the connection list or own bands
 * change.
 *
 * Falls back to account-based identity (the user's UID and the default demo band)
 * when no explicit hardware band is registered under `users/{uid}/bands/`.
 *
 * Returns an empty list when no account is signed in.
 *
 * Nothing here logs a uid or device id.
 */
class FirebaseBandRepository(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : BandRepository {

    override fun observeBands(): Flow<List<PairedBand>> {
        val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
        return callbackFlow {
            val watcherWearersRef = database.reference
                .child(NODE_WATCHER_WEARERS).child(uid)
            val ownBandsRef = database.reference
                .child(NODE_USERS).child(uid).child(NODE_BANDS)

            /**
             * Full rebuild: reads own bands + all watched wearers' bands and
             * emits the combined list.
             */
            fun rebuild(watcher_wearers: DataSnapshot) {
                val wearerUids = watcher_wearers.children.mapNotNull { it.key }
                launch {
                    val bands = mutableListOf<PairedBand>()

                    // Own (WEARER) bands:
                    // Read explicit bands if any; fallback to uid and DEFAULT_DEVICE_ID
                    runCatching {
                        val ownSnap = ownBandsRef.readOnce()
                        val explicitBands = ownSnap.children.mapNotNull { it.key }
                        if (explicitBands.isNotEmpty()) {
                            explicitBands.forEach { deviceId ->
                                bands += PairedBand(deviceId, BandRelation.WEARER)
                            }
                        } else {
                            bands += PairedBand(uid, BandRelation.WEARER)
                            bands += PairedBand(DeviceConstants.DEFAULT_DEVICE_ID, BandRelation.WEARER)
                        }
                    }.onFailure {
                        bands += PairedBand(uid, BandRelation.WEARER)
                        bands += PairedBand(DeviceConstants.DEFAULT_DEVICE_ID, BandRelation.WEARER)
                    }

                    // Watched (WATCHER) bands — one-shot read per wearer.
                    for (wearerUid in wearerUids) {
                        runCatching {
                            val wearerBandsSnap = database.reference
                                .child(NODE_USERS).child(wearerUid).child(NODE_BANDS)
                                .readOnce()
                            val explicitBands = wearerBandsSnap.children.mapNotNull { it.key }
                            if (explicitBands.isNotEmpty()) {
                                explicitBands.forEach { deviceId ->
                                    if (bands.none { it.deviceId == deviceId }) {
                                        bands += PairedBand(deviceId, BandRelation.WATCHER)
                                    }
                                }
                            }
                            if (bands.none { it.deviceId == wearerUid }) {
                                bands += PairedBand(wearerUid, BandRelation.WATCHER)
                            }
                        }.onFailure {
                            if (bands.none { it.deviceId == wearerUid }) {
                                bands += PairedBand(wearerUid, BandRelation.WATCHER)
                            }
                        }
                    }

                    trySend(bands)
                }
            }

            // Listen to the watcher_wearers list; rebuild on every change.
            val watcherListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = rebuild(snapshot)
                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList())
                }
            }

            // Also listen to own bands; on change, re-read watcher_wearers to
            // trigger a full rebuild (keeps the logic in one place).
            val ownBandsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    watcherWearersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) = rebuild(s)
                        override fun onCancelled(e: DatabaseError) {}
                    })
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            watcherWearersRef.addValueEventListener(watcherListener)
            ownBandsRef.addValueEventListener(ownBandsListener)
            awaitClose {
                watcherWearersRef.removeEventListener(watcherListener)
                ownBandsRef.removeEventListener(ownBandsListener)
            }
        }
    }

    /** One-shot read, suspending. Swallows cancellation on error (empty on cancel). */
    private suspend fun DatabaseReference.readOnce(): DataSnapshot =
        suspendCancellableCoroutine { cont ->
            addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (cont.isActive) cont.resume(s)
                }

                override fun onCancelled(e: DatabaseError) {
                    // Propagate nothing — the caller uses runCatching.
                }
            })
        }

    private companion object {
        const val NODE_WATCHER_WEARERS = "watcher_wearers"
        const val NODE_USERS           = "users"
        const val NODE_BANDS           = "bands"
    }
}
