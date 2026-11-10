package com.example.guardband.ui.device

/**
 * Render state for [InviteCodeDialogFragment].
 *
 * @param isLoading true while a Firebase call is in flight.
 * @param generatedCode the 6-character code the wearer shares, or null until generated.
 * @param statusMessage a success or error message to show, or null when there is nothing to say.
 * @param redeemed true when the watcher successfully redeemed a code and the dialog should close.
 */
data class InviteCodeUiState(
    val isLoading: Boolean = false,
    val generatedCode: String? = null,
    val statusMessage: String? = null,
    val redeemed: Boolean = false
)
