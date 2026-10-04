package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Sign-Up Step 2 — validates the city / region.
 *
 * No UiState: the step has no async work and never shows its progress bar.
 *
 * Flow: SignUpLocationActivity → SignUpContactsActivity
 */
class SignUpLocationViewModel : ViewModel() {

    private val _events = Channel<SignUpLocationEvent>(Channel.BUFFERED)
    val events: Flow<SignUpLocationEvent> = _events.receiveAsFlow()

    /**
     * [name] is the value received from step 1 and is forwarded as-is;
     * [location] is forwarded trimmed.
     */
    fun onNextClicked(name: String, location: String) {
        if (InputValidator.isBlank(location)) {
            _events.trySend(SignUpLocationEvent.ShowMessage(MSG_LOCATION_REQUIRED))
            return
        }
        _events.trySend(SignUpLocationEvent.NavigateToContacts(name, location.trim()))
    }

    companion object {
        const val MSG_LOCATION_REQUIRED = "Please enter your location."
    }
}
