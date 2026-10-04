package com.example.guardband.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Profile tab: the welcome header (moved from the old Dashboard) and the
 * signed-in user's details. Read-only; the rest of the tab's content is still
 * to be defined. No events: nothing on the screen acts yet.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(currentUserState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private fun currentUserState(): ProfileUiState {
        val user = authRepository.currentUser()
        return ProfileUiState(
            userName = user?.name?.takeUnless { it.isBlank() } ?: DEFAULT_USER_NAME,
            email = user?.email.orEmpty(),
            location = user?.location?.takeUnless { it.isBlank() } ?: DEFAULT_LOCATION
        )
    }

    companion object {
        const val DEFAULT_USER_NAME = "User"
        const val DEFAULT_LOCATION = "Location not set"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(RepositoryProvider.authRepository) }
        }
    }
}
