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
 * Forgot Password — Step 3.
 * User enters and confirms their new password.
 *
 * Receives: [EXTRA_EMAIL] from ForgotVerifyActivity.
 * Flow: ForgotNewPassActivity → ForgotSuccessActivity
 */
class ForgotNewPassActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }

    private lateinit var etNewPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etConfirmPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private val viewModel: ForgotNewPassViewModel by viewModels { ForgotNewPassViewModel.Factory }
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_newpass)

        email             = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        etNewPassword     = findViewById(R.id.et_forgot_new_password)
        etConfirmPassword = findViewById(R.id.et_forgot_confirm_password)
        btnSave           = findViewById(R.id.btn_forgot_save_password)
        progressBar       = findViewById(R.id.progress_forgot_newpass)

        btnSave.setOnClickListener {
            viewModel.onSaveClicked(
                email           = email,
                newPassword     = etNewPassword.text.toString(),
                confirmPassword = etConfirmPassword.text.toString()
            )
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

    private fun render(state: ForgotNewPassUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        btnSave.isEnabled = !state.isLoading
    }

    private fun handleEvent(event: ForgotNewPassEvent) {
        when (event) {
            is ForgotNewPassEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            ForgotNewPassEvent.NavigateToSuccess -> {
                startActivity(Intent(this, ForgotSuccessActivity::class.java))
                finish()
            }
        }
    }
}
