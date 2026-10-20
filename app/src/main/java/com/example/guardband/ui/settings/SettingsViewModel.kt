package com.example.guardband.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Settings (pushed over the Home host from Track's gear).
 *
 * Log Out lives here (moved from the old Dashboard, per the Figma). Account
 * information opens its own screen, which holds Delete account; Display
 * holds the theme (light, dark, or the phone's); Change password and Device
 * information (the band, once pairing exists) are placeholders until their
 * screens exist; About
 * holds the version and the Privacy Notice, which was a row here until
 * 2026-10-20.
 *
 * **Alert notifications** opens its own screen
 * ([com.example.guardband.ui.notifications.AlertNotificationsActivity]),
 * which holds the switch. It was a switch on this list, and this screen's
 * only state, until 2026-10-20; Settings now has events only.
 *
 * Flow: Settings → (Log Out) → LoginActivity (back-stack cleared)
 *       Settings → (Account information) → AccountActivity
 *       Settings → (Alert notifications) → AlertNotificationsActivity
 *       Settings → (Display) → DisplayActivity
 *       Settings → (About) → AboutActivity
 */
class SettingsViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** Account information: the account's details, and Delete account. */
    fun onAccountClicked() {
        _events.trySend(SettingsEvent.OpenAccount)
    }

    /** Alert notifications: its own screen, with the switch. */
    fun onAlertNotificationsClicked() {
        _events.trySend(SettingsEvent.OpenAlertNotifications)
    }

    /** Change password and Device information. */
    fun onPlaceholderRowClicked() {
        _events.trySend(SettingsEvent.ShowMessage(MSG_COMING_SOON))
    }

    /** Display: the app's theme. */
    fun onDisplayClicked() {
        _events.trySend(SettingsEvent.OpenDisplay)
    }

    /** About: the app's version and the Privacy Notice. */
    fun onAboutClicked() {
        _events.trySend(SettingsEvent.OpenAbout)
    }

    /** The Changelog row: the app's progress, as an in-app page. */
    fun onChangelogClicked() {
        _events.trySend(SettingsEvent.OpenChangelog)
    }

    fun onLogoutClicked() {
        authRepository.signOut()
        _events.trySend(SettingsEvent.NavigateToLogin)
    }

    companion object {
        const val MSG_COMING_SOON = "Coming soon."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(RepositoryProvider.authRepository)
            }
        }
    }
}
