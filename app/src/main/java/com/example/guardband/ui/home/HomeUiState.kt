package com.example.guardband.ui.home

import com.example.guardband.data.model.Role

/**
 * State for the Home host screen.
 *
 * @param gate whether the account check has passed, is checking, or failed.
 * @param role the user's role (GUARDIAN or DEPENDENT), or null if not yet determined.
 */
data class HomeUiState(
    val gate: HomeGateState = HomeGateState.CHECKING,
    val role: Role? = null
)
