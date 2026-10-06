package com.example.guardband.data.repository

import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.User
import com.example.guardband.testing.FakeUserProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `a new user gets a profile with name, email and an empty location`() = runTest {
        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = true)

        assertTrue(result!!.isSuccess)
        assertEquals(Triple(USER.name, USER.email, ""), profiles.saved[USER.id])
    }

    @Test
    fun `a new user is not asked whether the profile exists`() = runTest {
        ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = true)

        assertEquals(0, profiles.profileExistsCalls)
    }

    @Test
    fun `a returning user with a profile is left completely alone`() = runTest {
        profiles.profileExistsResult = Result.success(true)

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertNull(result)
        assertEquals(0, profiles.saveProfileCalls)
    }

    @Test
    fun `a returning user whose profile went missing gets it recreated`() = runTest {
        profiles.profileExistsResult = Result.success(false)

        val result = ProfileProvisioning.ensureProfile(profiles, USER, isNewUser = false)

        assertTrue(result!!.isSuccess)
        assertEquals(Triple(USER.name, USER.email, ""), profiles.saved[USER.id])
    }

    @Test
    fun `a failed existence read writes nothing, so a location can never be wiped`() = runTest {
        profiles.profileExistsResult = Result.failure(AuthError.Network)

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

    @Test(expected = CancellationException::class)
    fun `cancellation propagates instead of being swallowed`() = runTest {
        val cancelling = object : UserProfileRepository {
            override suspend fun saveProfile(
                uid: String,
                name: String,
                email: String,
                location: String
            ): Result<Unit> = throw CancellationException("cancelled mid-write")

            override suspend fun profileExists(uid: String): Result<Boolean> =
                Result.success(false)

            override suspend fun fetchProfile(uid: String): Result<User?> =
                Result.success(null)

            override suspend fun finalizeSignUp(
                uid: String,
                name: String,
                email: String,
                location: String,
                contacts: List<EmergencyContact>
            ): Result<Unit> = Result.success(Unit)
        }

        ProfileProvisioning.ensureProfile(cancelling, USER, isNewUser = true)
    }

    private companion object {
        val USER = User(
            id = "uid-google-1",
            name = "Google User",
            email = "google.user@example.com",
            location = ""
        )
    }
}
