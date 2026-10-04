package com.example.guardband.ui.home

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
 * Host-level logic for [HomeActivity]: the second auth gate and requests to
 * open the pushed screens (Settings, Notifications). No UiState — which
 * fragment is on screen is navigation, owned by the Activity.
 *
 * Scoped to the Activity, so tab Fragments share this instance through
 * `activityViewModels { HomeViewModel.Factory }` and forward top-bar clicks here.
 *
 * Auth gate: Splash only routes signed-in users here, but the in-memory
 * session dies with the process while Android can still restore this
 * Activity directly. A fresh ViewModel therefore re-checks the session.
 */
class HomeViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    init {
        if (!authRepository.isLoggedIn()) {
            _events.trySend(HomeEvent.NavigateToLogin)
        }
    }

    fun onSettingsClicked() {
        _events.trySend(HomeEvent.ShowSettings)
    }

    fun onNotificationsClicked() {
        _events.trySend(HomeEvent.ShowNotifications)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(RepositoryProvider.authRepository) }
        }
    }
}
