package com.example.guardband.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Profile tab: the welcome header and the signed-in user's details. Read-only.
 * No events: nothing on the screen acts yet.
 *
 * The name and email come from the session synchronously, so the header is
 * never blank, and the `users/{uid}` read then replaces them with the stored
 * record — see [AuthRepository.currentUser].
 *
 * A read that finds no record, or fails, leaves the session's values in place.
 * The session is the better fallback than an error screen here: the user's own
 * name and address are still worth showing.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(sessionState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = authRepository.currentUser()?.id
        if (uid == null) {
            // HomeViewModel's auth gate handles a dead session; this screen
            // just stops rather than reporting it twice.
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            userProfileRepository.fetchProfile(uid)
                .onSuccess { profile ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            userName = profile?.name?.takeUnless { it.isBlank() } ?: state.userName,
                            email = profile?.email?.takeUnless { it.isBlank() } ?: state.email
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    /** What the session alone can fill, before the record read lands. */
    private fun sessionState(): ProfileUiState {
        val user = authRepository.currentUser()
        return ProfileUiState(
            isLoading = true,
            userName = user?.name?.takeUnless { it.isBlank() } ?: DEFAULT_USER_NAME,
            email = user?.email.orEmpty()
        )
    }

    companion object {
        const val DEFAULT_USER_NAME = "User"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.userProfileRepository
                )
            }
        }
    }
}
