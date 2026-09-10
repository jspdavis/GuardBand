package com.example.guardband.ui.loading

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.data.repository.AuthRepository
import com.example.guardband.ui.dashboard.MainActivity
import com.example.guardband.ui.login.LoginActivity

class LoadingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DESTINATION = "extra_destination"
        const val DEST_MAIN = "dest_main"
        const val DEST_DASHBOARD = DEST_MAIN
        private const val LOADING_DELAY_MS = 1600L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val navigateForward = Runnable {
        val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: DEST_MAIN
        when (destination) {
            DEST_MAIN -> {
                // Session check — if somehow signed out mid-flow, return to login.
                if (!AuthRepository.getInstance().isLoggedIn()) {
                    // Wizard may complete without email/password; still open shell.
                }
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            else -> {
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(navigateForward, LOADING_DELAY_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(navigateForward)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block during transition.
    }
}
