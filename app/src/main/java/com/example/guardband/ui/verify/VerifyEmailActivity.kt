package com.example.guardband.ui.verify

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.BuildConfig
import com.example.guardband.R
import com.example.guardband.ui.reset.EmailActionLink
import com.example.guardband.ui.splash.SplashActivity
import kotlinx.coroutines.launch

/**
 * Verify email - opened by the link in Firebase's verification email.
 *
 * **Not exported.** Every Firebase email link reaches the app through the one
 * App Link on `ResetPasswordActivity`, which routes `mode=verifyEmail` (and any
 * mode the app does not handle) here with the link as this Intent's data. The
 * link is still re-checked with [EmailActionLink] rather than trusted, the same
 * way the reset screen treats its own.
 *
 * Shows one of four outcomes: verifying, verified, failed (with Retry only for
 * a lost connection), or "this link isn't supported". Continue goes through
 * Splash, which sends a signed-in user Home and anyone else to Login.
 *
 * **Nothing here logs**, and the code never leaves the ViewModel.
 *
 * View ids: tv_verify_title, tv_verify_message, progress_verify,
 * btn_verify_retry, btn_verify_continue.
 *
 * Flow: (verify link) → ResetPasswordActivity → VerifyEmailActivity → SplashActivity
 */
class VerifyEmailActivity : AppCompatActivity() {

    private val viewModel: VerifyEmailViewModel by viewModels { VerifyEmailViewModel.Factory }

    private lateinit var tvTitle: TextView
    private lateinit var tvMessage: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnRetry: Button
    private lateinit var btnContinue: Button

    /** Swallows Back while the code is being applied. */
    private val blockBackWhileBusy = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        tvTitle = findViewById(R.id.tv_verify_title)
        tvMessage = findViewById(R.id.tv_verify_message)
        progress = findViewById(R.id.progress_verify)
        btnRetry = findViewById(R.id.btn_verify_retry)
        btnContinue = findViewById(R.id.btn_verify_continue)

        btnRetry.setOnClickListener { viewModel.onRetryClicked() }
        btnContinue.setOnClickListener { viewModel.onContinueClicked() }
        onBackPressedDispatcher.addCallback(this, blockBackWhileBusy)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }

        // Only on a genuine cold start: a rotation keeps the ViewModel, and
        // applying a spent code a second time would only report it as used.
        if (savedInstanceState == null) handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val url = intent?.data?.toString()
        if (url == null) {
            viewModel.onLinkOpened(code = null, unsupported = false)
            return
        }
        val host = BuildConfig.RESET_LINK_HOST
        val path = BuildConfig.RESET_LINK_PATH
        val unsupported = EmailActionLink.routeOf(url, host, path) == EmailActionLink.Route.UNSUPPORTED
        viewModel.onLinkOpened(
            code = EmailActionLink.verifyEmailCodeFrom(url, host, path),
            unsupported = unsupported
        )
    }

    private fun render(state: VerifyEmailUiState) {
        tvTitle.setText(
            when (state.stage) {
                VerifyEmailStage.VERIFYING -> R.string.label_verify_title_verifying
                VerifyEmailStage.VERIFIED -> R.string.label_verify_title_verified
                VerifyEmailStage.FAILED -> R.string.label_verify_title_failed
                VerifyEmailStage.UNSUPPORTED -> R.string.label_verify_title_unsupported
            }
        )
        tvMessage.text = state.message.orEmpty()
        tvMessage.visibility = if (state.message == null) View.GONE else View.VISIBLE
        progress.visibility = if (state.isBusy) View.VISIBLE else View.GONE
        btnRetry.visibility = if (state.canRetry) View.VISIBLE else View.GONE
        btnContinue.isEnabled = !state.isBusy
        blockBackWhileBusy.isEnabled = state.isBusy
    }

    private fun handleEvent(event: VerifyEmailEvent) {
        when (event) {
            VerifyEmailEvent.Continue -> {
                startActivity(
                    Intent(this, SplashActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
        }
    }
}
