package com.example.guardband.testing

import com.example.guardband.data.repository.UserProfileRepository

/**
 * [UserProfileRepository] that records what was written, so a test can assert
 * whether a profile was created, left alone, or overwritten.
 *
 * Profiles are keyed by uid in [saved]. [profileExistsResult] is separate from
 * that map on purpose: a test needs to simulate a read failure without also
 * claiming the record is absent.
 */
class FakeUserProfileRepository(
    var saveProfileResult: Result<Unit> = Result.success(Unit),
    var profileExistsResult: Result<Boolean>? = null
) : UserProfileRepository {

    /** uid → (name, email, location), in call order. */
    val saved = mutableMapOf<String, Triple<String, String, String>>()

    var saveProfileCalls = 0; private set
    var profileExistsCalls = 0; private set

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

    /** Falls back to whether [saved] holds [uid] unless a test pins the answer. */
    override suspend fun profileExists(uid: String): Result<Boolean> {
        profileExistsCalls++
        return profileExistsResult ?: Result.success(saved.containsKey(uid))
    }
}
