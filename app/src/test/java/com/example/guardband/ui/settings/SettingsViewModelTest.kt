package com.example.guardband.ui.settings

import com.example.guardband.testing.FakeAlertNotificationPrefs
import com.example.guardband.testing.FakeAuthRepository
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Settings: what each row asks the Fragment to do, now that Account
 * information is a real screen and Delete account lives inside it, and the
 * Alert notifications switch, which is the one piece of state.
 */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(signedIn = true)
    private val prefs = FakeAlertNotificationPrefs()
    private val viewModel = SettingsViewModel(auth, prefs)

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

    // ── Alert notifications ───────────────────────────────────────────────────

    @Test
    fun `alert notifications are off by default`() {
        viewModel.onScreenShown(notificationsAllowed = true)

        assertEquals(SettingsUiState(alertNotificationsOn = false, notificationsBlocked = false), viewModel.uiState.value)
        assertFalse(prefs.watchEnabled)
    }

    /** Below API 33, or with the permission already granted. */
    @Test
    fun `switching on when the phone allows notifications starts the watch`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = true, canAskPermission = false)

        assertEquals(SettingsEvent.StartAlertWatch, viewModel.events.first())
        assertTrue(prefs.watchEnabled)
        assertTrue(viewModel.uiState.value.alertNotificationsOn)
    }

    @Test
    fun `switching on without the permission asks first and stays off`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = true)

        assertEquals(SettingsEvent.RequestNotificationPermission, viewModel.events.first())
        assertFalse(prefs.watchEnabled)
        assertEquals(SettingsUiState(alertNotificationsOn = false, notificationsBlocked = false), viewModel.uiState.value)
    }

    @Test
    fun `granting the permission turns it on and starts the watch`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = true)
        viewModel.events.first()

        viewModel.onNotificationPermissionResult(granted = true)

        assertEquals(SettingsEvent.StartAlertWatch, viewModel.events.first())
        assertTrue(prefs.watchEnabled)
        assertTrue(viewModel.uiState.value.alertNotificationsOn)
    }

    @Test
    fun `refusing leaves it off and offers notification settings`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = true)
        viewModel.events.first()

        viewModel.onNotificationPermissionResult(granted = false)

        assertFalse(prefs.watchEnabled)
        assertEquals(SettingsUiState(alertNotificationsOn = false, notificationsBlocked = true), viewModel.uiState.value)

        viewModel.onOpenNotificationSettingsClicked()
        assertEquals(SettingsEvent.OpenNotificationSettings, viewModel.events.first())
    }

    /** Blocked in system settings, or a permanent refusal: no prompt to show, so no loop. */
    @Test
    fun `switching on when blocked and unaskable shows blocked without a prompt`() {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = false)

        assertFalse(prefs.watchEnabled)
        assertEquals(SettingsUiState(alertNotificationsOn = false, notificationsBlocked = true), viewModel.uiState.value)
    }

    @Test
    fun `switching off stops the watch`() = runTest {
        prefs.watchEnabled = true
        viewModel.onScreenShown(notificationsAllowed = true)

        viewModel.onAlertNotificationsToggled(on = false, notificationsAllowed = true, canAskPermission = false)

        assertEquals(SettingsEvent.StopAlertWatch, viewModel.events.first())
        assertFalse(prefs.watchEnabled)
        assertFalse(viewModel.uiState.value.alertNotificationsOn)
    }

    @Test
    fun `an earlier choice is shown when the screen opens`() {
        prefs.watchEnabled = true

        viewModel.onScreenShown(notificationsAllowed = true)

        assertTrue(viewModel.uiState.value.alertNotificationsOn)
    }

    /** Turned off in system settings while the switch was on: say so, keep the wish. */
    @Test
    fun `notifications blocked in system settings show as blocked, not on`() {
        prefs.watchEnabled = true

        viewModel.onScreenShown(notificationsAllowed = false)

        assertEquals(SettingsUiState(alertNotificationsOn = false, notificationsBlocked = true), viewModel.uiState.value)
        assertTrue(prefs.watchEnabled)
    }

    @Test
    fun `the battery row opens the battery settings`() = runTest {
        viewModel.onBatterySettingsClicked()

        assertEquals(SettingsEvent.OpenBatterySettings, viewModel.events.first())
    }
}
