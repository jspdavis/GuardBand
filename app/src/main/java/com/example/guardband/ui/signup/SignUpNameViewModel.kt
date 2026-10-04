package com.example.guardband.ui.signup

import androidx.lifecycle.ViewModel
import com.example.guardband.utils.InputValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Sign-Up Step 1 — validates the full name.
 *
 * No UiState: the step has no async work and never shows its progress bar.
 *
 * Flow: SignUpNameActivity → SignUpLocationActivity
 */
class SignUpNameViewModel : ViewModel() {

    private val _events = Channel<SignUpNameEvent>(Channel.BUFFERED)
    val events: Flow<SignUpNameEvent> = _events.receiveAsFlow()

    /** Forwards the trimmed [name] to the next step. */
    fun onNextClicked(name: String) {
        if (InputValidator.isBlank(name)) {
            _events.trySend(SignUpNameEvent.ShowMessage(MSG_NAME_REQUIRED))
            return
        }
        _events.trySend(SignUpNameEvent.NavigateToLocation(name.trim()))
    }

    companion object {
        const val MSG_NAME_REQUIRED = "Please enter your full name."
    }
}
