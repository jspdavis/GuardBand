package com.example.guardband.ui.settings

/** One-shot events emitted by [SettingsViewModel]. */
sealed interface SettingsEvent {
    data class ShowMessage(val text: String) : SettingsEvent
    object NavigateToLogin : SettingsEvent
}
