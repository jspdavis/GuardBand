package com.example.guardband.ui.forgot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import kotlinx.coroutines.launch

/**
 * Forgot Password — Step 1.
 * User enters their email; a mock reset code is "sent".
 *
 * Flow: ForgotRequestActivity → ForgotVerifyActivity
 */
class ForgotRequestActivity : AppCompatActivity() {

    private lateinit var etEmail: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnSend: Button
    private lateinit var progressBar: ProgressBar

    private val viewModel: ForgotRequestViewModel by viewModels { ForgotRequestViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_request)

        etEmail     = findViewById(R.id.et_forgot_request_email)
        btnSend     = findViewById(R.id.btn_forgot_request_send)
        progressBar = findViewById(R.id.progress_forgot_request)

        btnSend.setOnClickListener {
            viewModel.onSendClicked(etEmail.text.toString())
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

    private fun render(state: ForgotRequestUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !state.isLoading
    }

    private fun handleEvent(event: ForgotRequestEvent) {
        when (event) {
            is ForgotRequestEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            is ForgotRequestEvent.NavigateToVerify ->
                startActivity(
                    Intent(this, ForgotVerifyActivity::class.java).apply {
                        putExtra(ForgotVerifyActivity.EXTRA_EMAIL, event.email)
                    }
                )
        }
    }
}
