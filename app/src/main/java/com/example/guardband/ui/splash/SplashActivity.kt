package com.example.guardband.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.auth.LoginActivity

/**
 * Entry point of the app.
 * Displays the brand splash screen for [SPLASH_DELAY_MS] ms then
 * navigates to [LoginActivity], clearing itself from the back-stack.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DELAY_MS = 2000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val navigateToLogin = Runnable {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(navigateToLogin, SPLASH_DELAY_MS)
    }

    override fun onPause() {
        super.onPause()
        // Cancel the delayed transition if the activity goes to background.
        handler.removeCallbacks(navigateToLogin)
    }
}
