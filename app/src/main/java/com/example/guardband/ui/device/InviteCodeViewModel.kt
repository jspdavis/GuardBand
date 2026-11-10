package com.example.guardband.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.guardband.data.RepositoryProvider
import com.example.guardband.data.repository.WearerConnectionError
import com.example.guardband.data.repository.WearerConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Invite-code operations for [InviteCodeDialogFragment].
 *
 * Calls [WearerConnectionRepository] and translates the results into
 * [InviteCodeUiState]. Firebase calls stay in the repository; this ViewModel
 * holds no context and logs nothing.
 */
class InviteCodeViewModel(
    private val connectionRepository: WearerConnectionRepository
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

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { InviteCodeViewModel(RepositoryProvider.wearerConnectionRepository) }
        }
    }
}
