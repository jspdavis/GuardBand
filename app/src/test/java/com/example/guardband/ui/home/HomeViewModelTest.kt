package com.example.guardband.ui.home

import com.example.guardband.data.model.ProfileState
import com.example.guardband.data.repository.AuthError
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.FakeUserProfileRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Home host's gate: no session goes to Login, and an account whose
 * deletion did not finish goes to finish it instead of into the tabs.
 *
 * Every way into the app passes through here - a restored session from
 * Splash, an email login, a Google sign-in - which is why the unfinished
 * deletion is caught here rather than in one sign-in path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeUserProfileRepository()
    private val uid = FakeAuthRepository.DEFAULT_USER.id

    /** Builds the gate and returns every event it sends on the way in. */
    private fun TestScope.open(signedIn: Boolean = true): List<HomeEvent> {
        val vm = HomeViewModel(FakeAuthRepository(signedIn = signedIn), profiles)
        val seen = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { seen += it }
        }
        advanceUntilIdle()
        return seen
    }

    @Test
    fun `no session goes to login without reading the profile`() = runTest {
        assertEquals(listOf<HomeEvent>(HomeEvent.NavigateToLogin), open(signedIn = false))
        assertEquals(0, profiles.profileStateCalls)
    }

    @Test
    fun `a live profile lets the user into the tabs`() = runTest {
        profiles.saveProfile(uid, "Alex Rivera", "alex@guardband.com")

        assertTrue(open().isEmpty())
    }

    @Test
    fun `an account with no record yet lets the user in`() = runTest {
        assertTrue(open().isEmpty())
    }

    /** Profile removed, Auth delete failed, user gave up: back to finish it. */
    @Test
    fun `an unfinished deletion is resumed instead of opening the tabs`() = runTest {
        profiles.pendingDeletion += uid

        assertEquals(listOf<HomeEvent>(HomeEvent.ResumeDeletion), open())
    }

    /** Offline or refused: let them in, as before the marker existed. */
    @Test
    fun `a failed marker read lets the user in rather than locking them out`() = runTest {
        profiles.profileStateResult = Result.failure(AuthError.Network)

        assertTrue(open().isEmpty())
    }

    @Test
    fun `another account's marker does not redirect this one`() = runTest {
        profiles.pendingDeletion += "uid-somebody-else"

        assertTrue(open().isEmpty())
    }

    @Test
    fun `a pinned pending state is honoured`() = runTest {
        profiles.profileStateResult = Result.success(ProfileState.DELETION_PENDING)

        assertEquals(listOf<HomeEvent>(HomeEvent.ResumeDeletion), open())
    }
}
