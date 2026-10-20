package com.example.guardband.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Alert notifications (opened from its row in Settings): the opt-in switch
 * for the alert watch ([AlertWatchService]). It was a switch on the Settings
 * list itself until 2026-10-20.
 *
 * Off by default. Turning it on asks for the notification permission first
 * where the phone has one (API 33+), from the tap, never at launch; a
 * refusal leaves it off and offers the system page. Whether the phone allows
 * notifications is the Activity's to read (it needs a Context), so it is
 * passed in with each call.
 */
class AlertNotificationsViewModel(
    private val alertPrefs: AlertNotificationPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertNotificationsUiState())
    val uiState: StateFlow<AlertNotificationsUiState> = _uiState.asStateFlow()

    private val _events = Channel<AlertNotificationsEvent>(Channel.BUFFERED)
    val events: Flow<AlertNotificationsEvent> = _events.receiveAsFlow()

    /** The prompt was refused this time the screen was open. */
    private var refused = false

    /**
     * The screen came to the front. Notifications may have been turned on or
     * off in system settings meanwhile.
     */
    fun onScreenShown(notificationsAllowed: Boolean) {
        render(notificationsAllowed)
    }

    /**
     * The switch was flipped.
     *
     * @param notificationsAllowed the phone lets the app post right now.
     * @param canAskPermission the system prompt can be shown (API 33+, not
     *   granted). False below API 33, where blocked means blocked in settings.
     */
    fun onAlertNotificationsToggled(on: Boolean, notificationsAllowed: Boolean, canAskPermission: Boolean) {
        when {
            !on -> {
                alertPrefs.watchEnabled = false
                _events.trySend(AlertNotificationsEvent.StopAlertWatch)
                render(notificationsAllowed)
            }
            notificationsAllowed -> enableWatch()
            canAskPermission -> {
                // Stays off until the answer comes back.
                render(notificationsAllowed)
                _events.trySend(AlertNotificationsEvent.RequestNotificationPermission)
            }
            else -> {
                refused = true
                render(notificationsAllowed = false)
            }
        }
    }

    /** The system prompt's answer. */
    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            enableWatch()
        } else {
            refused = true
            render(notificationsAllowed = false)
        }
    }

    /** "Open notification settings", under a blocked switch. */
    fun onOpenNotificationSettingsClicked() {
        _events.trySend(AlertNotificationsEvent.OpenNotificationSettings)
    }

    /** "Keep watching while the phone sleeps". */
    fun onBatterySettingsClicked() {
        _events.trySend(AlertNotificationsEvent.OpenBatterySettings)
    }

    /** A system settings page this phone does not have. */
    fun onSystemSettingsUnavailable() {
        _events.trySend(AlertNotificationsEvent.ShowMessage(MSG_NO_SYSTEM_SETTINGS))
    }

    private fun enableWatch() {
        alertPrefs.watchEnabled = true
        refused = false
        _events.trySend(AlertNotificationsEvent.StartAlertWatch)
        render(notificationsAllowed = true)
    }

    private fun render(notificationsAllowed: Boolean) {
        val wanted = alertPrefs.watchEnabled
        _uiState.update {
            it.copy(
                alertNotificationsOn = wanted && notificationsAllowed,
                notificationsBlocked = !notificationsAllowed && (wanted || refused)
            )
        }
    }

    companion object {
        const val MSG_NO_SYSTEM_SETTINGS =
            "This phone has no settings page for that. Look under Apps → GuardBand."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AlertNotificationsViewModel(
                    AlertNotificationPrefs.from(checkNotNull(this[APPLICATION_KEY]))
                )
            }
        }
    }
}
