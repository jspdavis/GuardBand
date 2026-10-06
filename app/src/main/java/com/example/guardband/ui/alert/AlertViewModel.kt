package com.example.guardband.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.AlertRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Alert tab: the band's ongoing status (latest alert) and its incident
 * history, observed live for as long as the ViewModel lives.
 *
 * Adapted from the checkpoint's ReceiverViewModel: one UiState instead of four
 * flows, errors as events, and no device id (the repository owns it).
 * A failed read keeps the last good data on screen and shows a message.
 */
class AlertViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertUiState(isLoading = true))
    val uiState: StateFlow<AlertUiState> = _uiState.asStateFlow()

    private val _events = Channel<AlertEvent>(Channel.BUFFERED)
    val events: Flow<AlertEvent> = _events.receiveAsFlow()

    init {
        observeAlerts()
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            combine(
                alertRepository.observeLatestAlert(),
                alertRepository.observeAlertHistory()
            ) { latest, history -> latest to history }
                .collect { (latestResult, historyResult) ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            latest = latestResult.getOrElse { state.latest },
                            history = historyResult.getOrNull()?.alerts ?: state.history
                        )
                    }
                    (latestResult.exceptionOrNull() ?: historyResult.exceptionOrNull())
                        ?.let { error ->
                            _events.send(AlertEvent.ShowMessage(error.message ?: MSG_LOAD_FAILED))
                        }
                }
        }
    }

    companion object {
        const val MSG_LOAD_FAILED = "Couldn't load alerts from your band."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AlertViewModel(RepositoryProvider.alertRepository) }
        }
    }
}
