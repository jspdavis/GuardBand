package com.example.guardband.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.data.repository.AlertError
import com.example.guardband.data.repository.AlertHistory
import com.example.guardband.data.repository.AlertRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Alert tab: the band's ongoing status and its incident history, observed live
 * for as long as the ViewModel lives.
 *
 * **It reads history only.** The latest card is the newest non-tracking entry
 * in that same window (D2), not `devices/{id}/latest`, because `latest` is
 * overwritten by every payload including a TRACKING_UPDATE - so the card would
 * otherwise read "Location update" while a panic sat one row below it.
 * [AlertRepository.observeLatestAlert] still exists for the Track tab; not
 * subscribing to it here also means one fewer live listener.
 *
 * D1 hides tracking updates, D3 reads a window of
 * [AlertRepository.DEFAULT_HISTORY_WINDOW] raw entries and shows at most
 * [MAX_HISTORY] of them.
 *
 * A failed read keeps the last good data on screen and reports it as an event;
 * only a failure with nothing to fall back to becomes the error state.
 */
class AlertViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertUiState())
    val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

    private val _events = Channel<AlertEvent>(Channel.BUFFERED)
    val events: Flow<AlertEvent> = _events.receiveAsFlow()

    /** The live subscription, cancelled and replaced on retry. */
    private var observeJob: Job? = null

    init {
        observeHistory()
    }

    /**
     * Re-subscribes after a failure.
     *
     * Cancelling [observeJob] cancels the repository's `callbackFlow`, whose
     * `awaitClose` removes the Realtime Database listener, so a retry replaces
     * the listener instead of stacking a second one on top of it.
     */
    fun onRetryClicked() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeHistory()
    }

    private fun observeHistory() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            alertRepository.observeAlertHistory(AlertRepository.DEFAULT_HISTORY_WINDOW)
                .collect { result ->
                    result
                        .onSuccess { history -> render(history) }
                        .onFailure { error -> reportFailure(error) }
                }
        }
    }

    private fun render(history: AlertHistory) {
        // D1 then D3: drop tracking updates, then cap what is left.
        val displayable = history.alerts
            .filter { it.alertType() != AlertType.TRACKING_UPDATE }
            .take(MAX_HISTORY)

        _uiState.update {
            it.copy(
                isLoading = false,
                // D2: the newest surviving entry is the current status.
                latest = displayable.firstOrNull(),
                history = displayable,
                errorMessage = null,
                emptyMessage = emptyMessageFor(history, displayable)
            )
        }
    }

    /**
     * Why there is nothing to show.
     *
     * The three cases read identically as an empty list but mean different
     * things to someone wondering whether their band is working, which is why
     * [AlertHistory] carries its counts.
     */
    private fun emptyMessageFor(history: AlertHistory, displayable: List<Alert>): String? = when {
        displayable.isNotEmpty() -> null
        history.rawCount == 0 -> MSG_EMPTY_NO_ALERTS
        history.malformedCount == history.rawCount -> MSG_EMPTY_UNREADABLE
        else -> MSG_EMPTY_ONLY_TRACKING
    }

    /**
     * A failure with content on screen is a notice; a failure with nothing on
     * screen is the error state, because there the user has no other signal
     * that anything went wrong.
     */
    private suspend fun reportFailure(error: Throwable) {
        val message = messageFor(error)
        val hasContent = _uiState.value.history.isNotEmpty()

        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (hasContent) null else message,
                emptyMessage = null
            )
        }

        if (hasContent) _events.send(AlertEvent.ShowMessage(message))
    }

    /**
     * Wording for each [AlertError].
     *
     * Kept in Kotlin rather than `strings.xml` for the reason the other
     * ViewModels do it: a ViewModel holds no Context. Nothing here repeats a
     * Firebase message - [AlertError] carries none.
     */
    private fun messageFor(error: Throwable): String = when (error) {
        AlertError.Network -> MSG_NETWORK
        AlertError.PermissionDenied -> MSG_PERMISSION_DENIED
        AlertError.NotSignedIn -> MSG_NOT_SIGNED_IN
        AlertError.ParseFailure -> MSG_UNREADABLE
        else -> MSG_LOAD_FAILED
    }

    companion object {
        /** D3's display cap, applied after tracking updates are dropped. */
        const val MAX_HISTORY = 50

        const val MSG_LOAD_FAILED = "Couldn't load alerts from your band."
        const val MSG_NETWORK = "Couldn't reach your band. Check your connection."
        const val MSG_PERMISSION_DENIED = "You don't have access to this band's alerts."
        const val MSG_NOT_SIGNED_IN = "Please sign in again to see your band's alerts."
        const val MSG_UNREADABLE = "Your band sent something we couldn't read."

        const val MSG_EMPTY_NO_ALERTS = "No alerts from your band yet."
        const val MSG_EMPTY_ONLY_TRACKING =
            "No incidents recently — your band has only been sending location updates."
        const val MSG_EMPTY_UNREADABLE = "Your band's recent alerts couldn't be read."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AlertViewModel(RepositoryProvider.alertRepository) }
        }
    }
}
