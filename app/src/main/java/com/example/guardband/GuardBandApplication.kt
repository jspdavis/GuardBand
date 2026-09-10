package com.example.guardband

import android.app.Application
import com.google.firebase.FirebaseApp

class GuardBandApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
