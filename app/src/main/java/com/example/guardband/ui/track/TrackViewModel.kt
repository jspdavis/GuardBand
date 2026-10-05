package com.example.guardband.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Track tab: the user chip, plus the map controls and the Check-in pill.
 *
 * The map and its controls are UI only for now, and so is Check-in: FR-10
 * defines check-in as a double-press on the band, so the app doesn't send one.
 * Those clicks only explain that. The settings and bell icons are host
 * navigation and go to HomeViewModel, not here.
 */
class TrackViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(sessionState())
    val uiState: StateFlow<TrackUiState> = _uiState.asStateFlow()

    private val _events = Channel<TrackEvent>(Channel.BUFFERED)
    val events: Flow<TrackEvent> = _events.receiveAsFlow()

    init {
        loadLocation()
    }

    fun onCheckInClicked() {
        _events.trySend(TrackEvent.ShowMessage(MSG_CHECK_IN_ON_BAND))
    }

    fun onRecenterClicked() {
        _events.trySend(TrackEvent.ShowMessage(MSG_MAP_UNAVAILABLE))
    }

    fun onLayersClicked() {
        _events.trySend(TrackEvent.ShowMessage(MSG_MAP_UNAVAILABLE))
    }

    /**
     * Fills the chip's location from `users/{uid}`, which the session cannot
     * supply - see [AuthRepository.currentUser].
     *
     * A missing record or a failed read leaves [DEFAULT_LOCATION] in place.
     * The chip is not worth an error state: the map beside it is the screen's
     * subject, and HomeViewModel's gate already covers a dead session.
     */
    private fun loadLocation() {
        val uid = authRepository.currentUser()?.id ?: return

        viewModelScope.launch {
            userProfileRepository.fetchProfile(uid).onSuccess { profile ->
                val location = profile?.location?.takeUnless { it.isBlank() } ?: return@onSuccess
                _uiState.update { it.copy(userLocation = location) }
            }
        }
    }

    /** What the session alone can fill: the name, but never the location. */
    private fun sessionState(): TrackUiState {
        val user = authRepository.currentUser()
        return TrackUiState(
            userName = user?.name?.takeUnless { it.isBlank() } ?: DEFAULT_USER_NAME,
            userLocation = DEFAULT_LOCATION
        )
    }

    companion object {
        const val DEFAULT_USER_NAME = "User"
        const val DEFAULT_LOCATION = "Location not set"
        const val MSG_CHECK_IN_ON_BAND =
            "Check-ins come from the band: double-press its button."
        const val MSG_MAP_UNAVAILABLE = "The live map isn't available yet."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TrackViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
