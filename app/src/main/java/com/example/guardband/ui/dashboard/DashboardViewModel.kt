package com.example.guardband.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.data.repository.ContactRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dashboard — welcome header, emergency contacts, and logout.
 *
 * Loads once when created (the presenter loaded in onCreate).
 *
 * Flow: DashboardActivity → (Logout) → LoginActivity (back-stack cleared)
 */
class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(isLoading = true, userName = currentUserName())
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events: Flow<DashboardEvent> = _events.receiveAsFlow()

    init {
        loadContacts()
    }

    fun onLogoutClicked() {
        authRepository.signOut()
        _events.trySend(DashboardEvent.NavigateToLogin)
    }

    /** A failure is shown as an empty list, as before: the old contacts API had no error path. */
    private fun loadContacts() {
        viewModelScope.launch {
            val contacts = contactRepository.getContacts().getOrDefault(emptyList())
            _uiState.update { it.copy(isLoading = false, contacts = contacts) }
        }
    }

    /** Session user's name, or [DEFAULT_USER_NAME] when signed out or blank. */
    private fun currentUserName(): String =
        authRepository.currentUser()?.name?.takeUnless { it.isBlank() } ?: DEFAULT_USER_NAME

    companion object {
        const val DEFAULT_USER_NAME = "User"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    RepositoryProvider.authRepository,
                    RepositoryProvider.contactRepository
                )
            }
        }
    }
}
