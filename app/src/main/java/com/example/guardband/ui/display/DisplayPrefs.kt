package com.example.guardband.ui.display

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * The one thing Display remembers on this phone: the [ThemeChoice]. Per
 * phone, not per account, like the alert watch's switch.
 */
interface DisplayPrefs {
    var themeChoice: ThemeChoice

    companion object {
        /** The live store. `RepositoryProvider` holds no Context, so callers that have one build it. */
        fun from(context: Context): DisplayPrefs =
            SharedPreferencesDisplayPrefs(
                context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            )

        /**
         * Puts the saved choice into effect. AppCompat keeps the night mode
         * in memory only, so the Application calls this on every start,
         * before any Activity is created; otherwise a choice of Light on a
         * dark phone would last only until the process died.
         */
        fun applySaved(context: Context) {
            AppCompatDelegate.setDefaultNightMode(from(context).themeChoice.nightMode)
        }

        private const val FILE_NAME = "display"
    }
}

class SharedPreferencesDisplayPrefs(
    private val prefs: SharedPreferences
) : DisplayPrefs {

    override var themeChoice: ThemeChoice
        get() = ThemeChoice.fromStored(prefs.getString(KEY_THEME, null))
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    private companion object {
        const val KEY_THEME = "theme"
    }
}
