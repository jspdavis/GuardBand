package com.example.guardband.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.BandError
import com.example.guardband.data.repository.BandIdInput
import com.example.guardband.data.repository.BandRepository
import com.example.guardband.data.repository.WearerConnectionError
import com.example.guardband.data.repository.WearerConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Invite-code operations for [InviteCodeDialogFragment], and claiming your
 * own band by its id (pairing stage 1, [claimBand]).
 *
 * Calls [WearerConnectionRepository] and translates the results into
 * [InviteCodeUiState]. Firebase calls stay in the repository; this ViewModel
 * holds no context and logs nothing.
 */
class InviteCodeViewModel(
    private val connectionRepository: WearerConnectionRepository,
    private val bandRepository: BandRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteCodeUiState())
    val uiState: StateFlow<InviteCodeUiState> = _uiState.asStateFlow()

    /** Generate a new invite code and display it. */
    fun generate() {
        _uiState.update { it.copy(isLoading = true, statusMessage = null, generatedCode = null) }
        viewModelScope.launch {
            connectionRepository.generateInviteCode().fold(
                onSuccess = { code ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generatedCode = code,
                            statusMessage = MSG_CODE_GENERATED
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = messageFor(error)) }
                }
            )
        }
    }

    /** Redeem [code] entered by the watcher. */
    fun redeem(code: String) {
        if (code.length != 6) {
            _uiState.update { it.copy(statusMessage = MSG_INVALID_FORMAT) }
            return
        }
        _uiState.update { it.copy(isLoading = true, statusMessage = null) }
        viewModelScope.launch {
            connectionRepository.redeemInviteCode(code).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, redeemed = true) } },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = messageFor(error)) }
                }
            )
        }
    }

    /**
     * Claims the band whose id the user typed (pairing stage 1): it becomes
     * the band this account wears, replacing any before. The id is checked by
     * [BandIdInput] first, so nothing malformed reaches the database.
     */
    fun claimBand(text: String) {
        val deviceId = BandIdInput.deviceIdOf(text)
        if (deviceId == null) {
            _uiState.update { it.copy(statusMessage = MSG_BAND_ID_INVALID) }
            return
        }
        _uiState.update { it.copy(isLoading = true, statusMessage = null) }
        viewModelScope.launch {
            bandRepository.claimBand(deviceId).fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, bandClaimed = true) } },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, statusMessage = claimMessageFor(error)) }
                }
            )
        }
    }

    private fun claimMessageFor(error: Throwable): String = when (error) {
        BandError.NotSignedIn      -> MSG_NOT_SIGNED_IN
        BandError.PermissionDenied -> MSG_CLAIM_REFUSED
        else                       -> MSG_NETWORK_ERROR
    }

    private fun messageFor(error: Throwable): String = when (error) {
        WearerConnectionError.NotSignedIn        -> MSG_NOT_SIGNED_IN
        WearerConnectionError.CodeNotFound       -> MSG_CODE_NOT_FOUND
        WearerConnectionError.CodeExpired        -> MSG_CODE_EXPIRED
        WearerConnectionError.AlreadyLinked      -> MSG_ALREADY_LINKED
        WearerConnectionError.PermissionDenied   -> MSG_PERMISSION_DENIED
        else                                     -> MSG_NETWORK_ERROR
    }

    companion object {
        const val MSG_CODE_GENERATED      = "Code ready — share it with your watcher."
        const val MSG_INVALID_FORMAT      = "Enter the 6-character code exactly as shown."
        const val MSG_NOT_SIGNED_IN       = "Sign in again to pair."
        const val MSG_CODE_NOT_FOUND      = "That code wasn't found. Check and try again."
        const val MSG_CODE_EXPIRED        = "That code has expired. Ask for a new one."
        const val MSG_ALREADY_LINKED      = "You're already the wearer for that code."
        const val MSG_PERMISSION_DENIED   = "Database permission denied. Have the invite_codes rules been published in Firebase Console?"
        const val MSG_NETWORK_ERROR       = "Couldn't reach the server. Check your connection."
        const val MSG_BAND_ID_INVALID     = "That doesn't look like a band id. Check it and try again."
        const val MSG_CLAIM_REFUSED       = "Couldn't save your band. Try again later."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InviteCodeViewModel(
                    RepositoryProvider.wearerConnectionRepository,
                    RepositoryProvider.bandRepository
                )
            }
        }
    }
}
