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
 * Settings (pushed over the Home host from Track's gear). No UiState — the
 * screen is static.
 *
 * Log Out lives here (moved from the old Dashboard, per the Figma). The four
 * rows are placeholders until their screens exist.
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

    fun onLogoutClicked() {
        authRepository.signOut()
        _events.trySend(SettingsEvent.NavigateToLogin)
    }

    companion object {
        const val MSG_COMING_SOON = "Coming soon."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(RepositoryProvider.authRepository) }
        }
    }
}
