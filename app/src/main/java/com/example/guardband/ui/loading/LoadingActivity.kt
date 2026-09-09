package com.example.guardband.ui.loading

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.dashboard.DashboardActivity

/**
 * Transient loading screen shown between auth actions and the Dashboard.
 *
 * Callers pass [EXTRA_DESTINATION] to tell LoadingActivity where to go next.
 * Currently only [DEST_DASHBOARD] is defined; add more destinations as needed.
 *
 * Automatically advances after [LOADING_DELAY_MS] to simulate a network call.
 */
class LoadingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DESTINATION = "extra_destination"
        const val DEST_DASHBOARD    = "dest_dashboard"
        private const val LOADING_DELAY_MS = 1800L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val navigateForward = Runnable {
        val destination = intent.getStringExtra(EXTRA_DESTINATION)
        when (destination) {
            DEST_DASHBOARD -> {
                startActivity(
                    Intent(this, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            else -> {
                startActivity(
                    Intent(this, DashboardActivity::class.java).apply {
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

    /** Prevent the user from pressing Back to escape the loading screen. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally blocked during loading transition.
    }
}
