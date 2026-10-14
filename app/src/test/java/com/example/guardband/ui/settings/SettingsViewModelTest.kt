package com.example.guardband.ui.settings

import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Settings: a static list. What each row asks the Fragment to do, now that
 * Account information is a real screen and Delete account lives inside it.
 */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(signedIn = true)
    private val viewModel = SettingsViewModel(auth)

    @Test
    fun `the Account information row opens Account information`() = runTest {
        viewModel.onAccountClicked()

        assertEquals(SettingsEvent.OpenAccount, viewModel.events.first())
    }

    @Test
    fun `the remaining placeholder rows still say coming soon`() = runTest {
        viewModel.onPlaceholderRowClicked()

        assertEquals(
            SettingsEvent.ShowMessage(SettingsViewModel.MSG_COMING_SOON),
            viewModel.events.first()
        )
    }

    @Test
    fun `opening Account information signs nobody out`() = runTest {
        viewModel.onAccountClicked()
        viewModel.events.first()

        assertEquals(0, auth.signOutCalls)
        assertFalse(auth.currentUser() == null)
    }

    @Test
    fun `Log Out signs out and goes to Login`() = runTest {
        viewModel.onLogoutClicked()

        assertEquals(SettingsEvent.NavigateToLogin, viewModel.events.first())
        assertEquals(1, auth.signOutCalls)
    }
}
