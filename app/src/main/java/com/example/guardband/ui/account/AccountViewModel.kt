package com.example.guardband.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Account information: who is signed in. Delete account, at the bottom of the
 * same screen, is [DeleteAccountViewModel]'s.
 *
 * Everything comes from the session, synchronously, so the screen is never
 * blank. The email shown is the one the account signs in with, which is the
 * one that matters here; Profile is where the stored `users/{uid}` record is
 * read.
 *
 * **Its own auth gate.** [AccountActivity] is outside the Home host, so
 * [HomeViewModel][com.example.guardband.ui.home.HomeViewModel]'s check does
 * not cover it; a restore with no session goes to Login instead of showing an
 * empty account.
 *
 * Nothing here logs: not the name, not the email.
 */
class AccountViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events: Flow<AccountEvent> = _events.receiveAsFlow()

    init {
        val user = authRepository.currentUser()
        if (user == null) {
            _events.trySend(AccountEvent.NavigateToLogin)
        } else {
            _uiState.value = AccountUiState(name = user.name, email = user.email)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(RepositoryProvider.authRepository) }
        }
    }
}
