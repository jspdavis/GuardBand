package com.example.guardband.testing

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User
import com.example.guardband.data.repository.UserProfileRepository

/**
 * [UserProfileRepository] that records what was written, so a test can assert
 * whether a profile was created, left alone, or overwritten.
 *
 * Profiles are keyed by uid in [saved]. [profileExistsResult] is separate from
 * that map on purpose: a test needs to simulate a read failure without also
 * claiming the record is absent, and [fetchProfileResult] exists for the same
 * reason.
 */
class FakeUserProfileRepository(
    var saveProfileResult: Result<Unit> = Result.success(Unit),
    var profileExistsResult: Result<Boolean>? = null,
    var fetchProfileResult: Result<User?>? = null,
    var finalizeSignUpResult: Result<Unit> = Result.success(Unit)
) : UserProfileRepository {

    /** uid → (name, email, location), in call order. */
    val saved = mutableMapOf<String, Triple<String, String, String>>()

    /** uid → the contacts passed to [finalizeSignUp]. */
    val finalizedContacts = mutableMapOf<String, List<EmergencyContact>>()

    var saveProfileCalls = 0; private set
    var profileExistsCalls = 0; private set
    var fetchProfileCalls = 0; private set
    var finalizeSignUpCalls = 0; private set

    override suspend fun saveProfile(
        uid: String,
        name: String,
        email: String,
        location: String
    ): Result<Unit> {
        saveProfileCalls++
        if (saveProfileResult.isSuccess) {
            saved[uid] = Triple(name, email, location)
        }
        return saveProfileResult
    }

    /** Falls back to [saved] unless a test pins the answer. */
    override suspend fun fetchProfile(uid: String): Result<User?> {
        fetchProfileCalls++
        fetchProfileResult?.let { return it }

        val record = saved[uid] ?: return Result.success(null)
        return Result.success(
            User(id = uid, name = record.first, email = record.second, location = record.third)
        )
    }

    /** Falls back to whether [saved] holds [uid] unless a test pins the answer. */
    override suspend fun profileExists(uid: String): Result<Boolean> {
        profileExistsCalls++
        return profileExistsResult ?: Result.success(saved.containsKey(uid))
    }

    /**
     * Records the profile and the contacts together, so a test can assert the
     * all-or-nothing behaviour: on a pinned failure **neither** is recorded.
     */
    override suspend fun finalizeSignUp(
        uid: String,
        name: String,
        email: String,
        location: String,
        contacts: List<EmergencyContact>
    ): Result<Unit> {
        finalizeSignUpCalls++
        if (finalizeSignUpResult.isSuccess) {
            saved[uid] = Triple(name, email, location)
            finalizedContacts[uid] = contacts
        }
        return finalizeSignUpResult
    }
}
