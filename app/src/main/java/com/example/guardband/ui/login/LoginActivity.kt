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
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.GoogleIdTokenResult
import com.example.guardband.ui.forgot.ForgotRequestActivity
import com.example.guardband.ui.loading.LoadingActivity
import com.example.guardband.ui.signup.SignUpLocationActivity
import com.example.guardband.ui.signup.SignUpNameActivity
import kotlinx.coroutines.launch

/**
 * Login screen — entry point for returning users.
 *
 * View ids: et_login_email, et_login_password, btn_login, login_google_button,
 * progress_login, tv_login_signup, tv_login_forgot, til_login_email,
 * til_login_password, iv_login_logo, tv_login_title.
 *
 * Google sign-in: the chooser needs an Activity, so [GoogleIdTokenProvider]
 * runs here and the ViewModel only ever receives the token string or a
 * [GoogleIdTokenResult].
 *
 * Flow:
 *   Login  →  LoadingActivity  →  HomeActivity
 *   Login  →  SignUpNameActivity
 *   Login  →  ForgotRequestActivity
 *   Login  →  (Google, returning user)  →  LoadingActivity  →  HomeActivity
 *   Login  →  (Google, first-time user) →  SignUpLocationActivity (complete profile)
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView
    private lateinit var tvForgot: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGoogle: Button

    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail     = findViewById(R.id.et_login_email)
        etPassword  = findViewById(R.id.et_login_password)
        btnLogin    = findViewById(R.id.btn_login)
        tvSignUp    = findViewById(R.id.tv_login_signup)
        tvForgot    = findViewById(R.id.tv_login_forgot)
        progressBar = findViewById(R.id.progress_login)
        btnGoogle   = findViewById(R.id.login_google_button)

        btnLogin.setOnClickListener {
            viewModel.onLoginClicked(
                email    = etEmail.text.toString(),
                password = etPassword.text.toString()
            )
        }

        btnGoogle.setOnClickListener { viewModel.onGoogleSignInClicked() }

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
        progressBar.visibility = if (state.isBusy) View.VISIBLE else View.GONE
        // Both buttons go flat while either sign-in runs, so neither can start
        // a second attempt on top of the first.
        btnLogin.isEnabled = !state.isBusy
        btnGoogle.isEnabled = !state.isBusy
    }

    private fun handleEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            LoginEvent.NavigateToLoadingHome -> {
                startActivity(
                    Intent(this, LoadingActivity::class.java).apply {
                        putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_HOME)
                    }
                )
                finish()
            }

            LoginEvent.NavigateToSignUp ->
                startActivity(Intent(this, SignUpNameActivity::class.java))

            LoginEvent.NavigateToForgotPassword ->
                startActivity(Intent(this, ForgotRequestActivity::class.java))

            is LoginEvent.NavigateToCompleteProfile ->
                startActivity(
                    Intent(this, SignUpLocationActivity::class.java).apply {
                        putExtra(SignUpLocationActivity.EXTRA_NAME, event.name)
                        putExtra(SignUpLocationActivity.EXTRA_EMAIL, event.email)
                        putExtra(SignUpLocationActivity.EXTRA_COMPLETE_PROFILE, true)
                    }
                )

            LoginEvent.RequestGoogleIdToken -> requestGoogleIdToken()
        }
    }

    /**
     * Opens the Google account chooser and forwards the outcome.
     *
     * Runs in [lifecycleScope], so leaving the screen cancels the request; the
     * provider rethrows the cancellation rather than reporting a failure.
     */
    private fun requestGoogleIdToken() {
        lifecycleScope.launch {
            when (val result = googleIdTokenProvider.requestIdToken()) {
                is GoogleIdTokenResult.Success -> viewModel.onGoogleIdToken(result.idToken)
                else -> viewModel.onGoogleError(result)
            }
        }
    }
}
