package com.example.guardband.ui.home

/** One-shot events emitted by [HomeViewModel]. */
sealed interface HomeEvent {
    object NavigateToLogin : HomeEvent
    object ShowSettings : HomeEvent
    object ShowNotifications : HomeEvent

    /** Switch to the Profile tab, as if its bottom-bar item had been tapped. */
    object ShowProfile : HomeEvent
}
