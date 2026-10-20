package com.example.guardband.ui.notifications

import com.example.guardband.testing.FakeAlertNotificationPrefs
import com.example.guardband.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Alert notifications screen's switch: off by default, the permission
 * asked from the tap, a refusal or a block in system settings shown as
 * blocked, never as on. These were Settings' tests until the switch moved to
 * its own screen on 2026-10-20.
 */
class AlertNotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefs = FakeAlertNotificationPrefs()
    private val viewModel = AlertNotificationsViewModel(prefs)

    @Test
    fun `alert notifications are off by default`() {
        viewModel.onScreenShown(notificationsAllowed = true)

        assertEquals(AlertNotificationsUiState(alertNotificationsOn = false, notificationsBlocked = false), viewModel.uiState.value)
        assertFalse(prefs.watchEnabled)
    }

    /** Below API 33, or with the permission already granted. */
    @Test
    fun `switching on when the phone allows notifications starts the watch`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = true, canAskPermission = false)

        assertEquals(AlertNotificationsEvent.StartAlertWatch, viewModel.events.first())
        assertTrue(prefs.watchEnabled)
        assertTrue(viewModel.uiState.value.alertNotificationsOn)
    }

    @Test
    fun `switching on without the permission asks first and stays off`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = true)

        assertEquals(AlertNotificationsEvent.RequestNotificationPermission, viewModel.events.first())
        assertFalse(prefs.watchEnabled)
        assertEquals(AlertNotificationsUiState(alertNotificationsOn = false, notificationsBlocked = false), viewModel.uiState.value)
    }

    @Test
    fun `granting the permission turns it on and starts the watch`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = true)
        viewModel.events.first()

        viewModel.onNotificationPermissionResult(granted = true)

        assertEquals(AlertNotificationsEvent.StartAlertWatch, viewModel.events.first())
        assertTrue(prefs.watchEnabled)
        assertTrue(viewModel.uiState.value.alertNotificationsOn)
    }

    @Test
    fun `refusing leaves it off and offers notification settings`() = runTest {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = true)
        viewModel.events.first()

        viewModel.onNotificationPermissionResult(granted = false)

        assertFalse(prefs.watchEnabled)
        assertEquals(AlertNotificationsUiState(alertNotificationsOn = false, notificationsBlocked = true), viewModel.uiState.value)

        viewModel.onOpenNotificationSettingsClicked()
        assertEquals(AlertNotificationsEvent.OpenNotificationSettings, viewModel.events.first())
    }

    /** Blocked in system settings, or a permanent refusal: no prompt to show, so no loop. */
    @Test
    fun `switching on when blocked and unaskable shows blocked without a prompt`() {
        viewModel.onAlertNotificationsToggled(on = true, notificationsAllowed = false, canAskPermission = false)

        assertFalse(prefs.watchEnabled)
        assertEquals(AlertNotificationsUiState(alertNotificationsOn = false, notificationsBlocked = true), viewModel.uiState.value)
    }

    @Test
    fun `switching off stops the watch`() = runTest {
        prefs.watchEnabled = true
        viewModel.onScreenShown(notificationsAllowed = true)

        viewModel.onAlertNotificationsToggled(on = false, notificationsAllowed = true, canAskPermission = false)

        assertEquals(AlertNotificationsEvent.StopAlertWatch, viewModel.events.first())
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

        assertEquals(AlertNotificationsUiState(alertNotificationsOn = false, notificationsBlocked = true), viewModel.uiState.value)
        assertTrue(prefs.watchEnabled)
    }

    @Test
    fun `the battery row opens the battery settings`() = runTest {
        viewModel.onBatterySettingsClicked()

        assertEquals(AlertNotificationsEvent.OpenBatterySettings, viewModel.events.first())
    }

    @Test
    fun `a missing system page is explained, not a crash`() = runTest {
        viewModel.onSystemSettingsUnavailable()

        assertEquals(
            AlertNotificationsEvent.ShowMessage(AlertNotificationsViewModel.MSG_NO_SYSTEM_SETTINGS),
            viewModel.events.first()
        )
    }
}
