package com.example.guardband.ui.forgot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R

/**
 * Forgot Password — Step 3.
 * User enters and confirms their new password.
 *
 * Receives: [EXTRA_EMAIL] from ForgotVerifyActivity.
 * Flow: ForgotNewPassActivity → ForgotSuccessActivity
 */
class ForgotNewPassActivity : AppCompatActivity(), ForgotContract.NewPasswordView {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }

    private lateinit var etNewPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etConfirmPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private val presenter = ForgotNewPasswordPresenter()
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_newpass)

        email             = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        etNewPassword     = findViewById(R.id.et_forgot_new_password)
        etConfirmPassword = findViewById(R.id.et_forgot_confirm_password)
        btnSave           = findViewById(R.id.btn_forgot_save_password)
        progressBar       = findViewById(R.id.progress_forgot_newpass)

        presenter.attachView(this)

        btnSave.setOnClickListener {
            presenter.onSavePasswordClicked(
                email           = email,
                newPassword     = etNewPassword.text.toString(),
                confirmPassword = etConfirmPassword.text.toString()
            )
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── ForgotContract.NewPasswordView ────────────────────────────────────────

    override fun navigateToSuccess() {
        startActivity(Intent(this, ForgotSuccessActivity::class.java))
        finish()
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
        btnSave.isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
