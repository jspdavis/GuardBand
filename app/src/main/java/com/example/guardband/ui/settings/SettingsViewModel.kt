package com.example.guardband.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.ui.notifications.AlertNotificationPrefs
import com.example.guardband.utils.HostedPages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Settings (pushed over the Home host from Track's gear).
 *
 * Log Out lives here (moved from the old Dashboard, per the Figma). Account
 * information opens its own screen, which holds Delete account; Change
 * password is a placeholder until its screen exists; the Privacy Notice row
 * opens a hosted page.
 *
 * **Alert notifications** is the one switch, and the only state
 * ([SettingsUiState]). Off by default. Turning it on asks for the
 * notification permission first where the phone has one (API 33+), from the
 * tap, never at launch; a refusal leaves it off and offers the system page.
 * Whether the phone allows notifications is the Fragment's to read (it needs
 * a Context), so it is passed in with each call.
 *
 * Flow: Settings → (Log Out) → LoginActivity (back-stack cleared)
 *       Settings → (Account information) → AccountActivity
 */
class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val alertPrefs: AlertNotificationPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** The prompt was refused this time the screen was open. */
    private var refused = false

    /**
     * The screen came to the front. Notifications may have been turned on or
     * off in system settings meanwhile.
     */
    fun onScreenShown(notificationsAllowed: Boolean) {
        render(notificationsAllowed)
    }

    /**
     * The switch was flipped.
     *
     * @param notificationsAllowed the phone lets the app post right now.
     * @param canAskPermission the system prompt can be shown (API 33+, not
     *   granted). False below API 33, where blocked means blocked in settings.
     */
    fun onAlertNotificationsToggled(on: Boolean, notificationsAllowed: Boolean, canAskPermission: Boolean) {
        when {
            !on -> {
                alertPrefs.watchEnabled = false
                _events.trySend(SettingsEvent.StopAlertWatch)
                render(notificationsAllowed)
            }
            notificationsAllowed -> enableWatch()
            canAskPermission -> {
                // Stays off until the answer comes back.
                render(notificationsAllowed)
                _events.trySend(SettingsEvent.RequestNotificationPermission)
            }
            else -> {
                refused = true
                render(notificationsAllowed = false)
            }
        }
    }

    /** The system prompt's answer. */
    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            enableWatch()
        } else {
            refused = true
            render(notificationsAllowed = false)
        }
    }

    /** "Open notification settings", under a blocked switch. */
    fun onOpenNotificationSettingsClicked() {
        _events.trySend(SettingsEvent.OpenNotificationSettings)
    }

    /** "Keep watching while the phone sleeps". */
    fun onBatterySettingsClicked() {
        _events.trySend(SettingsEvent.OpenBatterySettings)
    }

    private fun enableWatch() {
        alertPrefs.watchEnabled = true
        refused = false
        _events.trySend(SettingsEvent.StartAlertWatch)
        render(notificationsAllowed = true)
    }

    private fun render(notificationsAllowed: Boolean) {
        val wanted = alertPrefs.watchEnabled
        _uiState.update {
            it.copy(
                alertNotificationsOn = wanted && notificationsAllowed,
                notificationsBlocked = !notificationsAllowed && (wanted || refused)
            )
        }
    }

    /** Account information: the account's details, and Delete account. */
    fun onAccountClicked() {
        _events.trySend(SettingsEvent.OpenAccount)
    }

    /** Change password. */
    fun onPlaceholderRowClicked() {
        _events.trySend(SettingsEvent.ShowMessage(MSG_COMING_SOON))
    }

    /**
     * The Privacy Notice row, which opens the hosted page.
     *
     * The one privacy row. A "Privacy & security" placeholder sat above it
     * until 2026-10-19 and only said "Coming soon", so it was removed.
     */
    fun onPrivacyNoticeClicked() {
        _events.trySend(SettingsEvent.OpenUrl(HostedPages.privacyNotice))
    }

    /** The Changelog row: the app's progress, as an in-app page. */
    fun onChangelogClicked() {
        _events.trySend(SettingsEvent.OpenChangelog)
    }

    /** No app on the phone can open a web page. */
    fun onBrowserUnavailable() {
        _events.trySend(SettingsEvent.ShowMessage(MSG_NO_BROWSER))
    }

    /** A system settings page this phone does not have. */
    fun onSystemSettingsUnavailable() {
        _events.trySend(SettingsEvent.ShowMessage(MSG_NO_SYSTEM_SETTINGS))
    }

    fun onLogoutClicked() {
        authRepository.signOut()
        _events.trySend(SettingsEvent.NavigateToLogin)
    }

    companion object {
        const val MSG_COMING_SOON = "Coming soon."
        const val MSG_NO_BROWSER =
            "No app on this phone can open the Privacy Notice."
        const val MSG_NO_SYSTEM_SETTINGS =
            "This phone has no settings page for that. Look under Apps → GuardBand."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    RepositoryProvider.authRepository,
                    AlertNotificationPrefs.from(checkNotNull(this[APPLICATION_KEY]))
                )
            }
        }
    }
}
