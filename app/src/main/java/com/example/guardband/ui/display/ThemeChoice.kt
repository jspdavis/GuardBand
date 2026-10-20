package com.example.guardband.ui.display

import androidx.appcompat.app.AppCompatDelegate

/**
 * Light, dark, or whatever the phone is set to: Settings → Display.
 * [SYSTEM] is the default, so a phone in dark mode gets a dark app without
 * anyone opening Settings.
 */
enum class ThemeChoice(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        val DEFAULT = SYSTEM

        /** A stored name back to a choice; anything unknown or missing is [DEFAULT]. */
        fun fromStored(name: String?): ThemeChoice =
            entries.find { it.name == name } ?: DEFAULT
    }
}
