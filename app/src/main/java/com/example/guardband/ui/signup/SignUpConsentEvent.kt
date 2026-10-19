package com.example.guardband.ui.signup

/** One-shot events emitted by [SignUpConsentViewModel]. */
sealed interface SignUpConsentEvent {
    data class ShowMessage(val text: String) : SignUpConsentEvent

    /**
     * The account and profile are written. Go to [WelcomeActivity] with the
     * back stack cleared; being the only route there is what makes Welcome a
     * once-per-account screen.
     */
    object NavigateToWelcome : SignUpConsentEvent

    /**
     * Open the Privacy Notice in the browser.
     *
     * Carries the URL rather than letting the Activity build one, so
     * [com.example.guardband.utils.HostedPages] stays the only place a URL on
     * the Hosting domain is spelled out.
     */
    data class OpenPrivacyNotice(val url: String) : SignUpConsentEvent
}
