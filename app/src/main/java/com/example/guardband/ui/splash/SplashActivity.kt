package com.example.guardband.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.home.HomeActivity
import com.example.guardband.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Entry point of the app.
 * Displays the brand splash screen while [SplashViewModel] counts down, then
 * navigates to [HomeActivity] (signed in) or [LoginActivity] (signed out),
 * clearing itself from the back-stack.
 */
class SplashActivity : AppCompatActivity() {

    private val viewModel: SplashViewModel by viewModels { SplashViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        SplashEvent.NavigateToLogin -> {
                            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                            finish()
                        }
                        SplashEvent.NavigateToHome -> {
                            startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
                            finish()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startCountdown()
    }

    override fun onPause() {
        super.onPause()
        // Cancel the countdown if the activity goes to background.
        viewModel.cancelCountdown()
    }
}
