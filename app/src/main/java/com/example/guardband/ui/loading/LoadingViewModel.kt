package com.example.guardband.ui.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Drives the loading countdown and decides where to go next. No UiState —
 * the screen is static.
 *
 * The Activity starts the countdown in onResume and cancels it in onPause,
 * so it restarts from zero every time the screen resumes.
 */
class LoadingViewModel : ViewModel() {

    private val _events = Channel<LoadingEvent>(Channel.BUFFERED)
    val events: Flow<LoadingEvent> = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    /**
     * (Re)starts the [LOADING_DELAY_MS] countdown, then emits the event for
     * [destination] (the raw `EXTRA_DESTINATION` value, possibly null).
     */
    fun startCountdown(destination: String?) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            delay(LOADING_DELAY_MS)
            _events.send(eventFor(destination))
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    /** Only [DEST_DASHBOARD] is defined today; unknown values also go to the Dashboard. */
    private fun eventFor(destination: String?): LoadingEvent =
        when (destination) {
            DEST_DASHBOARD -> LoadingEvent.NavigateToDashboard
            else -> LoadingEvent.NavigateToDashboard
        }

    companion object {
        const val DEST_DASHBOARD = "dest_dashboard"
        private const val LOADING_DELAY_MS = 1800L
    }
}
