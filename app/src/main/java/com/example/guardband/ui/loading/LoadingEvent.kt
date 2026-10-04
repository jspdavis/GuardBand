package com.example.guardband.ui.loading

/** One-shot events emitted by [LoadingViewModel]. */
sealed interface LoadingEvent {
    object NavigateToHome : LoadingEvent
}
