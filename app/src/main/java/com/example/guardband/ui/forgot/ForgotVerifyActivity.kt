package com.example.guardband.ui.forgot

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
import kotlinx.coroutines.launch

/**
 * Forgot Password — Step 2.
 * User enters the 6-digit verification code (mock code: "123456").
 *
 * Receives: [EXTRA_EMAIL] from ForgotRequestActivity.
 * Flow: ForgotVerifyActivity → ForgotNewPassActivity
 */
class ForgotVerifyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }

    private lateinit var etCode: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnVerify: Button
    private lateinit var tvResend: TextView
    private lateinit var progressBar: ProgressBar

    private val viewModel: ForgotVerifyViewModel by viewModels { ForgotVerifyViewModel.Factory }
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_verify)

        email       = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        etCode      = findViewById(R.id.et_forgot_verify_code)
        btnVerify   = findViewById(R.id.btn_forgot_verify)
        tvResend    = findViewById(R.id.tv_forgot_resend)
        progressBar = findViewById(R.id.progress_forgot_verify)

        btnVerify.setOnClickListener {
            viewModel.onVerifyClicked(email, etCode.text.toString())
        }

        tvResend.setOnClickListener {
            viewModel.onResendClicked(email)
        }

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

    private fun render(state: ForgotVerifyUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        btnVerify.isEnabled = !state.isLoading
        tvResend.isEnabled  = !state.isLoading
    }

    private fun handleEvent(event: ForgotVerifyEvent) {
        when (event) {
            is ForgotVerifyEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            is ForgotVerifyEvent.NavigateToNewPassword ->
                startActivity(
                    Intent(this, ForgotNewPassActivity::class.java).apply {
                        putExtra(ForgotNewPassActivity.EXTRA_EMAIL, event.email)
                    }
                )
        }
    }
}
