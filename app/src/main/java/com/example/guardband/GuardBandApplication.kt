package com.example.guardband

import android.app.Application
import com.example.guardband.ui.display.DisplayPrefs

/**
 * The app's one Application class. It does a single thing: applies the
 * theme chosen in Settings → Display before any Activity is created, since
 * AppCompat keeps that choice in memory only. Nothing else belongs here
 * without a reason as strong; Firebase starts itself.
 */
class GuardBandApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DisplayPrefs.applySaved(this)
    }
}
