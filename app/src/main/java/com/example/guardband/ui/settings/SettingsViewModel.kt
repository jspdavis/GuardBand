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
 * Log Out lives here (moved from the old Dashboard, per the Figma). The first
 * four rows are placeholders until their screens exist; the Privacy Notice row
 * below them opens a hosted page, so it is the one that already works.
 *
 * Flow: Settings → (Log Out) → LoginActivity (back-stack cleared)
 */
class SettingsViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** Account information, Change password, Notifications or Privacy & security. */
    fun onPlaceholderRowClicked() {
        _events.trySend(SettingsEvent.ShowMessage(MSG_COMING_SOON))
    }

    /**
     * The Privacy Notice row, which opens the hosted page.
     *
     * Separate from the "Privacy & security" placeholder above it: that one is
     * a settings screen nobody has built, this one is a document. Pointing the
     * placeholder at the notice would have made the row mean something other
     * than its label.
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
