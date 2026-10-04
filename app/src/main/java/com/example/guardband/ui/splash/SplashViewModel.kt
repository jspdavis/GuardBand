package com.example.guardband.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Drives the splash countdown and the first auth gate. No UiState — the
 * screen is static.
 *
 * When the countdown ends, a signed-in user goes straight to Home and everyone
 * else goes to Login. HomeViewModel re-checks the session (the second gate).
 *
 * The Activity starts the countdown in onResume and cancels it in onPause,
 * so it restarts from zero every time the screen resumes.
 */
class SplashViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<SplashEvent>(Channel.BUFFERED)
    val events: Flow<SplashEvent> = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    /** (Re)starts the [SPLASH_DELAY_MS] countdown, then emits the route for the session. */
    fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            delay(SPLASH_DELAY_MS)
            _events.send(
                if (authRepository.isLoggedIn()) SplashEvent.NavigateToHome
                else SplashEvent.NavigateToLogin
            )
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SplashViewModel(RepositoryProvider.authRepository) }
        }
    }
}
