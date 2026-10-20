package com.example.guardband.ui.display

/** One-shot events emitted by [DisplayViewModel]. */
sealed interface DisplayEvent {
    /**
     * Switch the app to [choice] now. AppCompat recreates every open
     * Activity, this one included, in the new theme.
     */
    data class ApplyTheme(val choice: ThemeChoice) : DisplayEvent
}
