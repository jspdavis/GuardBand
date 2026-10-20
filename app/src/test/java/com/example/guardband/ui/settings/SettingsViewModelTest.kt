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
 * Settings: what each row asks the Fragment to do, now that Account
 * information is a real screen and Delete account lives inside it. The
 * Alert notifications switch moved to its own screen on 2026-10-20
 * ([com.example.guardband.ui.notifications.AlertNotificationsViewModelTest]).
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
    fun `the Alert notifications row opens its own screen`() = runTest {
        viewModel.onAlertNotificationsClicked()

        assertEquals(SettingsEvent.OpenAlertNotifications, viewModel.events.first())
    }

    @Test
    fun `the About row opens About`() = runTest {
        viewModel.onAboutClicked()

        assertEquals(SettingsEvent.OpenAbout, viewModel.events.first())
    }

    @Test
    fun `the Display row opens Display`() = runTest {
        viewModel.onDisplayClicked()

        assertEquals(SettingsEvent.OpenDisplay, viewModel.events.first())
    }

    /** Change password and Device information. */
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
