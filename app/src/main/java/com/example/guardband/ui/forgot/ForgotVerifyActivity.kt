package com.example.guardband.ui.forgot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R

/**
 * Forgot Password — Step 2.
 * User enters the 6-digit verification code (mock code: "123456").
 *
 * Receives: [EXTRA_EMAIL] from ForgotRequestActivity.
 * Flow: ForgotVerifyActivity → ForgotNewPassActivity
 */
class ForgotVerifyActivity : AppCompatActivity(), ForgotContract.VerifyView {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }

    private lateinit var etCode: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnVerify: Button
    private lateinit var tvResend: TextView
    private lateinit var progressBar: ProgressBar

    private val presenter = ForgotVerifyPresenter()
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_verify)

        email       = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        etCode      = findViewById(R.id.et_forgot_verify_code)
        btnVerify   = findViewById(R.id.btn_forgot_verify)
        tvResend    = findViewById(R.id.tv_forgot_resend)
        progressBar = findViewById(R.id.progress_forgot_verify)

        presenter.attachView(this)

        btnVerify.setOnClickListener {
            presenter.onVerifyClicked(email, etCode.text.toString())
        }

        tvResend.setOnClickListener {
            presenter.onResendClicked(email)
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── ForgotContract.VerifyView ─────────────────────────────────────────────

    override fun navigateToNewPassword(email: String) {
        startActivity(
            Intent(this, ForgotNewPassActivity::class.java).apply {
                putExtra(ForgotNewPassActivity.EXTRA_EMAIL, email)
            }
        )
    }

    override fun showResendConfirmation() {
        Toast.makeText(this, "Code resent — check your email.", Toast.LENGTH_SHORT).show()
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnVerify.isEnabled = false
        tvResend.isEnabled  = false
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
        btnVerify.isEnabled = true
        tvResend.isEnabled  = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
