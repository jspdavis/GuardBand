package com.example.guardband.ui.about

import androidx.lifecycle.ViewModel
import com.example.guardband.utils.HostedPages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * About (opened from its row in Settings): the app's version, and the
 * Privacy Notice, which was a row of Settings itself until 2026-10-20.
 *
 * Events only: the version is a build constant the Activity reads, so there
 * is no state to hold. No dependencies either, so the Activity uses plain
 * `by viewModels()`.
 */
class AboutViewModel : ViewModel() {

    private val _events = Channel<AboutEvent>(Channel.BUFFERED)
    val events: Flow<AboutEvent> = _events.receiveAsFlow()

    /** The Privacy Notice row, which opens the hosted page. */
    fun onPrivacyNoticeClicked() {
        _events.trySend(AboutEvent.OpenUrl(HostedPages.privacyNotice))
    }

    /** No app on the phone can open a web page. */
    fun onBrowserUnavailable() {
        _events.trySend(AboutEvent.ShowMessage(MSG_NO_BROWSER))
    }

    companion object {
        const val MSG_NO_BROWSER =
            "No app on this phone can open the Privacy Notice."
    }
}
