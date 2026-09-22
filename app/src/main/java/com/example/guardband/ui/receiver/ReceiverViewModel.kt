package com.example.guardband.ui.receiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardband.data.model.Alert
import com.example.guardband.data.repository.AlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Receiver screen
 * Manages alert data from Firebase Realtime Database using real-time listeners
 */
class ReceiverViewModel : ViewModel() {
    private val alertRepository = AlertRepository()

    // Device ID - hardcoded to match mock sender
    private val deviceId = "guardband-001"

    // StateFlow for latest alert (real-time updates)
    private val _latestAlert = MutableStateFlow<Alert?>(null)
    val latestAlert: StateFlow<Alert?> = _latestAlert.asStateFlow()

    // StateFlow for alert history (real-time updates)
    private val _alertHistory = MutableStateFlow<List<Alert>>(emptyList())
    val alertHistory: StateFlow<List<Alert>> = _alertHistory.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        startObservingAlerts()
    }

    /**
     * Start observing alerts using Firebase real-time listeners
     */
    private fun startObservingAlerts() {
        // Observe latest alert
        viewModelScope.launch {
            alertRepository.observeLatestAlert(deviceId).collect { alert ->
                _latestAlert.value = alert
                _isLoading.value = false
            }
        }

        // Observe alert history
        viewModelScope.launch {
            alertRepository.observeAlertHistory(deviceId).collect { history ->
                _alertHistory.value = history
            }
        }
    }

    /**
     * Manually refresh alerts (fetch once)
     */
    fun refreshAlerts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Fetch latest alert
                val latest = alertRepository.fetchLatestAlert(deviceId)
                _latestAlert.value = latest

                // Fetch history
                val history = alertRepository.fetchAlertHistory(deviceId)
                _alertHistory.value = history

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to fetch alerts"
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}
