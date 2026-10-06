package com.example.guardband.data.repository

import com.example.guardband.data.model.Alert
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [AlertRepository] on the Realtime Database at `devices/{deviceId}`, matching
 * SCHEMA.md v1.0.
 *
 * **Ordering is by key, deliberately.** History children are keyed by
 * `sequenceId`, and RTDB sorts integer-like keys numerically and ahead of all
 * string keys, so `orderByKey().limitToLast(n)` returns the newest n entries.
 * `orderByChild("sequenceId")` would do the same but would need an
 * `".indexOn": "sequenceId"` rule deployed; `orderByKey` needs none. The
 * results are re-sorted by [Alert.sequenceId] in memory anyway, so a key that
 * is *not* integer-like (a push id, a zero-padded number) degrades the window
 * rather than the order within it.
 *
 * Failures are mapped by [DatabaseError.getCode] through
 * [FirebaseDatabaseErrorMapper] - see its KDoc for why the listener form is
 * used rather than `.await()`.
 *
 * Nothing here logs. An alert carries the wearer's coordinates, which is
 * exactly the kind of thing that must never reach logcat.
 *
 * @param database the shared instance from
 *   [FirebaseProvider][com.example.guardband.data.FirebaseProvider]; the URL
 *   comes from `google-services.json` and is never hardcoded.
 * @param deviceId the band being watched, supplied by
 *   [RepositoryProvider][com.example.guardband.data.RepositoryProvider].
 */
class FirebaseAlertRepository(
    database: FirebaseDatabase,
    private val deviceId: String
) : AlertRepository {

    private val deviceRef = database.getReference(PATH_DEVICES).child(deviceId)

    override fun observeLatestAlert(): Flow<Result<Alert?>> =
        deviceRef.child(PATH_LATEST).asFlow { snapshot ->
            when {
                // The band has sent nothing yet. Absence is a real answer, not
                // a failure.
                !snapshot.exists() -> Result.success(null)
                else -> AlertParser.parse(snapshot.value)
                    ?.let { Result.success(it) }
                    ?: Result.failure(AlertError.ParseFailure)
            }
        }

    override fun observeAlertHistory(limit: Int): Flow<Result<AlertHistory>> =
        deviceRef.child(PATH_HISTORY)
            .orderByKey()
            .limitToLast(limit)
            .asFlow { snapshot -> Result.success(snapshot.toHistory()) }

    /**
     * One malformed entry is skipped and counted, never fatal: a firmware bug
     * in a single row must not hide every good row behind it. The counting and
     * ordering live in [AlertParser.parseHistory] so they are unit-testable
     * without a live database.
     */
    private fun DataSnapshot.toHistory(): AlertHistory =
        AlertParser.parseHistory(children.map { it.value })

    /**
     * Wraps a [Query] in a [callbackFlow], mapping each snapshot with [onValue].
     *
     * The flow stays open on failure so a reconnect can still deliver data,
     * and [awaitClose] removes the listener when the collector cancels - which
     * is what keeps a cancelled Alert tab from leaking a live RTDB listener.
     */
    private fun <T> Query.asFlow(
        onValue: (DataSnapshot) -> Result<T>
    ): Flow<Result<T>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(onValue(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(FirebaseDatabaseErrorMapper.mapToAlertError(error)))
            }
        }

        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }

    private companion object {
        const val PATH_DEVICES = "devices"
        const val PATH_LATEST = "latest"
        const val PATH_HISTORY = "history"
    }
}
