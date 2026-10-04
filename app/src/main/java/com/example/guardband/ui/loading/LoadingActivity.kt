package com.example.guardband.ui.loading

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.home.HomeActivity
import kotlinx.coroutines.launch

/**
 * Transient loading screen shown between auth actions and Home.
 *
 * Callers pass [EXTRA_DESTINATION] to tell LoadingActivity where to go next.
 * Currently only [DEST_HOME] is defined; add more destinations as needed.
 *
 * Automatically advances after [LoadingViewModel]'s delay to simulate a network call.
 */
class LoadingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DESTINATION = "extra_destination"
        const val DEST_HOME    = LoadingViewModel.DEST_HOME
    }

    private val viewModel: LoadingViewModel by viewModels()
    private var destination: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        destination = intent.getStringExtra(EXTRA_DESTINATION)

        // Prevent the user from pressing Back to escape the loading screen.
        onBackPressedDispatcher.addCallback(this) {
            // Intentionally blocked during loading transition.
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startCountdown(destination)
    }

    override fun onPause() {
        super.onPause()
        viewModel.cancelCountdown()
    }

    private fun handleEvent(event: LoadingEvent) {
        when (event) {
            LoadingEvent.NavigateToHome -> {
                startActivity(
                    Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
        }
    }
}
