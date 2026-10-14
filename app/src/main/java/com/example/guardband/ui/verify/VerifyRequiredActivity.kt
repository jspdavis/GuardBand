package com.example.guardband.ui.verify

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.account.AccountActivity
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.LoginNavigation
import com.example.guardband.ui.home.HomeActivity
import com.example.guardband.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * "Verify your email" - the gate a password account meets instead of the tabs
 * until its address is verified. The Home host opens it with the task
 * cleared, so Back leaves the app rather than reaching the tabs.
 *
 * Re-checks every time it comes to the front, so tapping the link in the
 * email and returning is usually all it takes; "I've verified my email" does
 * the same on demand. See [VerifyRequiredViewModel].
 *
 * View ids: tv_verify_required_title, tv_verify_required_message,
 * btn_verify_required_check, btn_verify_required_resend,
 * btn_verify_required_log_out, btn_verify_required_delete,
 * progress_verify_required.
 *
 * Flow: Home gate → VerifyRequiredActivity → (verified) HomeActivity
 *       VerifyRequiredActivity → (Log Out) LoginActivity
 *       VerifyRequiredActivity → (Wrong email?) AccountActivity → (Back) here
 */
class VerifyRequiredActivity : AppCompatActivity() {

    private val viewModel: VerifyRequiredViewModel by viewModels {
        VerifyRequiredViewModel.Factory
    }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(this) }

    private lateinit var tvMessage: TextView
    private lateinit var btnCheck: Button
    private lateinit var btnResend: Button
    private lateinit var btnLogOut: Button
    private lateinit var btnDelete: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_required)

        tvMessage = findViewById(R.id.tv_verify_required_message)
        btnCheck = findViewById(R.id.btn_verify_required_check)
        btnResend = findViewById(R.id.btn_verify_required_resend)
        btnLogOut = findViewById(R.id.btn_verify_required_log_out)
        btnDelete = findViewById(R.id.btn_verify_required_delete)
        progress = findViewById(R.id.progress_verify_required)

        btnCheck.setOnClickListener { viewModel.onCheckClicked() }
        btnResend.setOnClickListener { viewModel.onResendClicked() }
        btnLogOut.setOnClickListener { viewModel.onLogOutClicked() }
        btnDelete.setOnClickListener { viewModel.onDeleteAccountClicked() }

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
    }

    /** Every return to the screen, including back from the email app. */
    override fun onStart() {
        super.onStart()
        viewModel.onScreenShown()
    }

    private fun render(state: VerifyRequiredUiState) {
        tvMessage.text = getString(R.string.label_verify_required_message, state.email)
        progress.visibility = if (state.isBusy) View.VISIBLE else View.GONE
        btnCheck.isEnabled = !state.isBusy
        btnLogOut.isEnabled = !state.isBusy
        btnDelete.isEnabled = !state.isBusy
        btnResend.isEnabled = state.resendEnabled
        btnResend.text = if (state.resendSecondsRemaining > 0) {
            getString(R.string.btn_account_resend_in, state.resendSecondsRemaining)
        } else {
            getString(R.string.btn_account_resend)
        }
    }

    private fun handleEvent(event: VerifyRequiredEvent) {
        when (event) {
            is VerifyRequiredEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()

            VerifyRequiredEvent.ContinueToHome -> replaceTaskWith(HomeActivity::class.java)

            VerifyRequiredEvent.NavigateToLogin -> replaceTaskWith(LoginActivity::class.java)

            VerifyRequiredEvent.LoggedOut -> lifecycleScope.launch {
                LoginNavigation.clearCredentialsThenGoToLogin(
                    this@VerifyRequiredActivity,
                    googleIdTokenProvider
                )
            }

            VerifyRequiredEvent.OpenAccount ->
                startActivity(Intent(this, AccountActivity::class.java))
        }
    }

    private fun replaceTaskWith(target: Class<out AppCompatActivity>) {
        startActivity(
            Intent(this, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
