package com.example.guardband.ui.home

/** One-shot events emitted by [HomeViewModel]. */
sealed interface HomeEvent {
    object NavigateToLogin : HomeEvent
    object ShowSettings : HomeEvent
    object ShowNotifications : HomeEvent

    /** Switch to the Profile tab, as if its bottom-bar item had been tapped. */
    object ShowProfile : HomeEvent

    /**
     * This account's deletion did not finish: its data is gone and its sign-in
     * is not. Leave the tabs for Account information, which offers to finish.
     */
    object ResumeDeletion : HomeEvent
}
