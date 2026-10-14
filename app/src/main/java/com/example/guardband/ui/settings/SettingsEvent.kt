package com.example.guardband.ui.settings

/** One-shot events emitted by [SettingsViewModel]. */
sealed interface SettingsEvent {
    data class ShowMessage(val text: String) : SettingsEvent
    object NavigateToLogin : SettingsEvent

    /**
     * Open a hosted page in the browser.
     *
     * Carries the URL rather than letting the Fragment build one, so
     * [com.example.guardband.utils.HostedPages] stays the only place a URL on
     * the Hosting domain is spelled out.
     */
    data class OpenUrl(val url: String) : SettingsEvent

    /** Open the in-app Changelog. */
    object OpenChangelog : SettingsEvent
}
