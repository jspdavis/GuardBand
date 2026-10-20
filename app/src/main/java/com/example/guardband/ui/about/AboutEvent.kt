package com.example.guardband.ui.about

/** One-shot events emitted by [AboutViewModel]. */
sealed interface AboutEvent {
    data class ShowMessage(val text: String) : AboutEvent

    /**
     * Open a hosted page in the browser.
     *
     * Carries the URL rather than letting the Activity build one, so
     * [com.example.guardband.utils.HostedPages] stays the only place a URL on
     * the Hosting domain is spelled out.
     */
    data class OpenUrl(val url: String) : AboutEvent
}
