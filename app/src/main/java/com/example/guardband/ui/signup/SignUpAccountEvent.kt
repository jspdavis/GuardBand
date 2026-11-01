package com.example.guardband.ui.signup

import com.example.guardband.data.model.Role

/** One-shot events emitted by [SignUpAccountViewModel]. */
sealed interface SignUpAccountEvent {
    data class ShowMessage(val text: String) : SignUpAccountEvent

    /**
     * Carries the credentials, selected role, and own-phone to the next step.
     */
    data class NavigateToName(
        val email: String,
        val password: String,
        val role: Role = Role.DEPENDENT,
        val ownPhone: String = ""
    ) : SignUpAccountEvent
}
