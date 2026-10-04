package com.example.guardband.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.forgot.ForgotRequestActivity
import com.example.guardband.ui.loading.LoadingActivity
import com.example.guardband.ui.signup.SignUpNameActivity
import kotlinx.coroutines.launch

/**
 * Login screen — entry point for returning users.
 *
 * Flow:
 *   Login  →  LoadingActivity  →  DashboardActivity
 *   Login  →  SignUpNameActivity
 *   Login  →  ForgotRequestActivity
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView
    private lateinit var tvForgot: TextView
    private lateinit var progressBar: ProgressBar

    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail     = findViewById(R.id.et_login_email)
        etPassword  = findViewById(R.id.et_login_password)
        btnLogin    = findViewById(R.id.btn_login)
        tvSignUp    = findViewById(R.id.tv_login_signup)
        tvForgot    = findViewById(R.id.tv_login_forgot)
        progressBar = findViewById(R.id.progress_login)

        btnLogin.setOnClickListener {
            viewModel.onLoginClicked(
                email    = etEmail.text.toString(),
                password = etPassword.text.toString()
            )
        }

        tvSignUp.setOnClickListener { viewModel.onSignUpClicked() }
        tvForgot.setOnClickListener { viewModel.onForgotPasswordClicked() }

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

    private fun render(state: LoginUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !state.isLoading
    }

    private fun handleEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            LoginEvent.NavigateToLoadingDashboard -> {
                startActivity(
                    Intent(this, LoadingActivity::class.java).apply {
                        putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_DASHBOARD)
                    }
                )
                finish()
            }

            LoginEvent.NavigateToSignUp ->
                startActivity(Intent(this, SignUpNameActivity::class.java))

            LoginEvent.NavigateToForgotPassword ->
                startActivity(Intent(this, ForgotRequestActivity::class.java))
        }
    }
}
