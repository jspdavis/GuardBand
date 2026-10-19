package com.example.guardband.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.utils.HostedPages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Settings (pushed over the Home host from Track's gear). No UiState — the
 * screen is static.
 *
 * Log Out lives here (moved from the old Dashboard, per the Figma). Account
 * information opens its own screen, which holds Delete account; the next two
 * rows are placeholders until their screens exist; the Privacy Notice row
 * opens a hosted page.
 *
 * Flow: Settings → (Log Out) → LoginActivity (back-stack cleared)
 *       Settings → (Account information) → AccountActivity
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

    /** Change password or Notifications. */
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

    fun onLogoutClicked() {
        authRepository.signOut()
        _events.trySend(SettingsEvent.NavigateToLogin)
    }

    companion object {
        const val MSG_COMING_SOON = "Coming soon."
        const val MSG_NO_BROWSER =
            "No app on this phone can open the Privacy Notice."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(RepositoryProvider.authRepository) }
        }
    }
}
