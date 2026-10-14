package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.model.User
import com.example.guardband.testing.FakeUserProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JUnit: the rule is pure Kotlin over [UserProfileRepository], with no
 * Android and no Firebase in reach, so it needs neither Robolectric nor a
 * mocking library.
 *
 * This is the part of `signInWithGoogle` that carries a decision. The Firebase
 * exchange itself is not covered here — it needs a real `FirebaseAuth`, and the
 * project declares no mocking library.
 */
class ProfileProvisioningTest {

    private val profiles = FakeUserProfileRepository()

    @Test
    fun `a new user gets a profile with their name and email`() = runTest {
        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = true)

        assertTrue(result!!.isSuccess)
        assertEquals(Pair(USER.name, USER.email), profiles.saved[USER.id])
    }

    @Test
    fun `a new user is not asked whether the profile exists`() = runTest {
        ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = true)

        assertEquals(0, profiles.profileStateCalls)
    }

    @Test
    fun `a returning user with a profile is left completely alone`() = runTest {
        profiles.profileStateResult = Result.success(ProfileState.PRESENT)

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertNull(result)
        assertEquals(0, profiles.saveProfileCalls)
    }

    @Test
    fun `a returning user whose profile went missing gets it recreated`() = runTest {
        profiles.profileStateResult = Result.success(ProfileState.ABSENT)

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertTrue(result!!.isSuccess)
        assertEquals(Pair(USER.name, USER.email), profiles.saved[USER.id])
    }

    @Test
    fun `a failed existence read writes nothing, so a record can never be clobbered`() = runTest {
        profiles.profileStateResult = Result.failure(AuthError.Network)

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertNull(result)
        assertEquals(0, profiles.saveProfileCalls)
    }

    @Test
    fun `a failed write is reported back, not thrown`() = runTest {
        profiles.saveProfileResult = Result.failure(AuthError.Network)

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = true)

        assertEquals(AuthError.Network, result!!.exceptionOrNull())
    }

    // ── The deletion marker ───────────────────────────────────────────────────

    /** The gap the marker closes: a half-deleted account must not come back thin. */
    @Test
    fun `an account with a deletion marker is not re-provisioned`() = runTest {
        profiles.profileStateResult = Result.success(ProfileState.DELETION_PENDING)

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertNull(result)
        assertEquals(0, profiles.saveProfileCalls)
    }

    /**
     * The whole failure path on paper, run against the fake: the data was
     * replaced by the marker, the Auth delete then failed, and the user signs
     * in again later.
     */
    @Test
    fun `signing in after a half-finished deletion writes nothing`() = runTest {
        profiles.saveProfile(USER.id, USER.name, USER.email)
        profiles.markDeletionPending(USER.id)
        val writesBefore = profiles.saveProfileCalls

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertNull(result)
        assertEquals(writesBefore, profiles.saveProfileCalls)
        assertFalse(profiles.saved.containsKey(USER.id))
        assertTrue(USER.id in profiles.pendingDeletion)
    }

    /** The marker is per uid: another account on the same phone is unaffected. */
    @Test
    fun `another account's marker does not stop this one being provisioned`() = runTest {
        profiles.pendingDeletion += "uid-somebody-else"

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertTrue(result!!.isSuccess)
        assertEquals(Pair(USER.name, USER.email), profiles.saved[USER.id])
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates instead of being swallowed`() = runTest {
        val cancelling = object : UserProfileRepository {
            override suspend fun saveProfile(
                uid: String,
                name: String,
                email: String
            ): Result<Unit> = throw CancellationException("cancelled mid-write")

            override suspend fun profileState(uid: String): Result<ProfileState> =
                Result.success(ProfileState.ABSENT)

            override suspend fun fetchProfile(uid: String): Result<User?> =
                Result.success(null)

            override suspend fun finalizeSignUp(
                uid: String,
                name: String,
                email: String,
                contacts: List<EmergencyContact>,
                consentVersion: String
            ): Result<Unit> = Result.success(Unit)

            override suspend fun markDeletionPending(uid: String): Result<Unit> =
                Result.success(Unit)
        }

        ProfileProvisioning.ensureProfile(cancelling, USER, isNewUser = true)
    }

    private companion object {
        val USER = User(
            id = "uid-google-1",
            name = "Google User",
            email = "google.user@example.com"
        )
    }
}
