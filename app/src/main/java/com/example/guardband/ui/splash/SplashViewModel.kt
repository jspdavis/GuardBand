package com.example.guardband.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Drives the splash countdown. No UiState — the screen is static.
 *
 * The Activity starts the countdown in onResume and cancels it in onPause,
 * so it restarts from zero every time the screen resumes.
 */
class SplashViewModel : ViewModel() {

    private val _events = Channel<SplashEvent>(Channel.BUFFERED)
    val events: Flow<SplashEvent> = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    /** (Re)starts the [SPLASH_DELAY_MS] countdown, then emits [SplashEvent.NavigateToLogin]. */
    fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            delay(SPLASH_DELAY_MS)
            _events.send(SplashEvent.NavigateToLogin)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2000L
    }
}
