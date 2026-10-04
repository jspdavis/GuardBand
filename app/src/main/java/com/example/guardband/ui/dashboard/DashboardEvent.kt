package com.example.guardband.ui.dashboard

/** One-shot events emitted by [DashboardViewModel]. */
sealed interface DashboardEvent {
    object NavigateToLogin : DashboardEvent
}
