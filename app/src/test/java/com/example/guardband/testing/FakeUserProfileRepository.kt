package com.example.guardband.testing

import com.example.guardband.data.model.ConsentRecord
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.model.User
import com.example.guardband.data.repository.UserProfileRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * [UserProfileRepository] that records what was written, so a test can assert
 * whether a profile was created, left alone, or overwritten.
 *
 * Profiles are keyed by uid in [saved], and deletion markers by uid in
 * [pendingDeletion], so a marker on one account says nothing about another.
 * [profileStateResult] is separate from both on purpose: a test needs to
 * simulate a read failure without also claiming the record is absent, and
 * [fetchProfileResult] and [consentRecordResult] exist for the same reason.
 *
 * Consent lives in [recordedConsent], written by both [finalizeSignUp] and
 * [recordConsent], so a test reads back one record whichever route made it.
 * [readsHeldUntil] holds every read open, so a test can look at a screen
 * while its read is still in flight.
 */
class FakeUserProfileRepository(
    var saveProfileResult: Result<Unit> = Result.success(Unit),
    var profileStateResult: Result<ProfileState>? = null,
    var fetchProfileResult: Result<User?>? = null,
    var finalizeSignUpResult: Result<Unit> = Result.success(Unit),
    var markDeletionPendingResult: Result<Unit> = Result.success(Unit),
    var consentRecordResult: Result<ConsentRecord?>? = null,
    var recordConsentResult: Result<Unit> = Result.success(Unit)
) : UserProfileRepository {

    /** While set and not completed, [profileState] and [consentRecord] wait on it. */
    var readsHeldUntil: CompletableDeferred<Unit>? = null

    /** uid → (name, email), in call order. */
    val saved = mutableMapOf<String, Pair<String, String>>()

    /** uid → the contacts passed to [finalizeSignUp]. */
    val finalizedContacts = mutableMapOf<String, List<EmergencyContact>>()

    /** uid → the consent version recorded with the write. */
    val recordedConsent = mutableMapOf<String, String>()

    var saveProfileCalls = 0; private set
    var profileStateCalls = 0; private set
    var fetchProfileCalls = 0; private set
    var finalizeSignUpCalls = 0; private set
    var markDeletionPendingCalls = 0; private set
    var consentRecordCalls = 0; private set
    var recordConsentCalls = 0; private set

    /** The uids passed to [markDeletionPending], in call order. */
    val markedUids = mutableListOf<String>()

    /** Uids whose record is only the deletion marker. A test may seed it. */
    val pendingDeletion = mutableSetOf<String>()

    override suspend fun saveProfile(
        uid: String,
        name: String,
        email: String
    ): Result<Unit> {
        saveProfileCalls++
        if (saveProfileResult.isSuccess) {
            saved[uid] = Pair(name, email)
        }
        return saveProfileResult
    }

    /** Falls back to [saved] unless a test pins the answer. */
    override suspend fun fetchProfile(uid: String): Result<User?> {
        fetchProfileCalls++
        fetchProfileResult?.let { return it }

        val record = saved[uid] ?: return Result.success(null)
        return Result.success(
            User(id = uid, name = record.first, email = record.second)
        )
    }

    /** Derived from [pendingDeletion] and [saved] unless a test pins the answer. */
    override suspend fun profileState(uid: String): Result<ProfileState> {
        profileStateCalls++
        readsHeldUntil?.await()
        profileStateResult?.let { return it }
        return Result.success(
            when (uid) {
                in pendingDeletion -> ProfileState.DELETION_PENDING
                in saved -> ProfileState.PRESENT
                else -> ProfileState.ABSENT
            }
        )
    }

    /** Derived from [recordedConsent] unless a test pins the answer. */
    override suspend fun consentRecord(uid: String): Result<ConsentRecord?> {
        consentRecordCalls++
        readsHeldUntil?.await()
        consentRecordResult?.let { return it }
        return Result.success(recordedConsent[uid]?.let { ConsentRecord(version = it) })
    }

    /** On success, records [consentVersion] for [uid] and nothing else. */
    override suspend fun recordConsent(uid: String, consentVersion: String): Result<Unit> {
        recordConsentCalls++
        if (recordConsentResult.isSuccess) {
            recordedConsent[uid] = consentVersion
        }
        return recordConsentResult
    }

    /**
     * On success, removes everything recorded for [uid] and leaves only the
     * marker - one step, as the real write is one commit - so a later
     * [profileState] reads [ProfileState.DELETION_PENDING].
     *
     * Marking twice still succeeds, which the retry path relies on.
     */
    override suspend fun markDeletionPending(uid: String): Result<Unit> {
        markDeletionPendingCalls++
        markedUids += uid
        if (markDeletionPendingResult.isSuccess) {
            saved.remove(uid)
            finalizedContacts.remove(uid)
            recordedConsent.remove(uid)
            pendingDeletion += uid
        }
        return markDeletionPendingResult
    }

    /**
     * Records the profile and the contacts together, so a test can assert the
     * all-or-nothing behaviour: on a pinned failure **neither** is recorded.
     */
    override suspend fun finalizeSignUp(
        uid: String,
        name: String,
        email: String,
        contacts: List<EmergencyContact>,
        consentVersion: String
    ): Result<Unit> {
        finalizeSignUpCalls++
        if (finalizeSignUpResult.isSuccess) {
            saved[uid] = Pair(name, email)
            finalizedContacts[uid] = contacts
            recordedConsent[uid] = consentVersion
        }
        return finalizeSignUpResult
    }
}
