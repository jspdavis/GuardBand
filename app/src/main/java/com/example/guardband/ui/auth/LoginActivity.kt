package com.example.guardband.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.forgot.ForgotRequestActivity
import com.example.guardband.ui.loading.LoadingActivity

/**
 * Login screen — entry point for returning users.
 *
 * Flow:
 *   Login  →  LoadingActivity  →  DashboardActivity
 *   Login  →  SignUpNameActivity
 *   Login  →  ForgotRequestActivity
 */
class LoginActivity : AppCompatActivity(), AuthContract.LoginView {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvSignUp: TextView
    private lateinit var tvForgot: TextView
    private lateinit var progressBar: ProgressBar

    private val presenter = LoginPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail     = findViewById(R.id.et_login_email)
        etPassword  = findViewById(R.id.et_login_password)
        btnLogin    = findViewById(R.id.btn_login)
        tvSignUp    = findViewById(R.id.tv_login_signup)
        tvForgot    = findViewById(R.id.tv_login_forgot)
        progressBar = findViewById(R.id.progress_login)

        presenter.attachView(this)

        btnLogin.setOnClickListener {
            presenter.onLoginClicked(
                email    = etEmail.text.toString(),
                password = etPassword.text.toString()
            )
        }

        tvSignUp.setOnClickListener { presenter.onSignUpClicked() }
        tvForgot.setOnClickListener { presenter.onForgotPasswordClicked() }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── AuthContract.LoginView ────────────────────────────────────────────────

    override fun navigateToDashboard() {
        startActivity(
            Intent(this, LoadingActivity::class.java).apply {
                putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_DASHBOARD)
            }
        )
        finish()
    }

    override fun navigateToSignUp() {
        startActivity(Intent(this, SignUpNameActivity::class.java))
    }

    override fun navigateToForgotPassword() {
        startActivity(Intent(this, ForgotRequestActivity::class.java))
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
        btnLogin.isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
