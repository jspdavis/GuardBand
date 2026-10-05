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
 * Mode-agnostic: it validates a location and forwards whatever it was given.
 * In complete-profile mode (Google sign-in) it is the *first* step the user
 * sees, since the name came from the Google account.
 *
 * Flow: SignUpLocationActivity → SignUpContactsActivity
 */
class SignUpLocationViewModel : ViewModel() {

    private val _events = Channel<SignUpLocationEvent>(Channel.BUFFERED)
    val events: Flow<SignUpLocationEvent> = _events.receiveAsFlow()

    /**
     * [name] is the value received from step 1 (or from the Google account) and
     * is forwarded as-is; [location] is forwarded trimmed. [email] and
     * [completeProfile] are carried straight through to the final step.
     */
    fun onNextClicked(
        name: String,
        location: String,
        email: String = "",
        completeProfile: Boolean = false
    ) {
        if (InputValidator.isBlank(location)) {
            _events.trySend(SignUpLocationEvent.ShowMessage(MSG_LOCATION_REQUIRED))
            return
        }
        _events.trySend(
            SignUpLocationEvent.NavigateToContacts(
                name = name,
                location = location.trim(),
                email = email,
                completeProfile = completeProfile
            )
        )
    }

    companion object {
        const val MSG_LOCATION_REQUIRED = "Please enter your location."
    }
}
