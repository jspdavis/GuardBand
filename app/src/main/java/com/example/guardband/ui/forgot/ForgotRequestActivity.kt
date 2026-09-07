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
 * Forgot Password — Step 1.
 * User enters their email; a mock reset code is "sent".
 *
 * Flow: ForgotRequestActivity → ForgotVerifyActivity
 */
class ForgotRequestActivity : AppCompatActivity(), ForgotContract.RequestView {

    private lateinit var etEmail: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnSend: Button
    private lateinit var progressBar: ProgressBar

    private val presenter = ForgotRequestPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_request)

        etEmail     = findViewById(R.id.et_forgot_request_email)
        btnSend     = findViewById(R.id.btn_forgot_request_send)
        progressBar = findViewById(R.id.progress_forgot_request)

        presenter.attachView(this)

        btnSend.setOnClickListener {
            presenter.onSendLinkClicked(etEmail.text.toString())
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── ForgotContract.RequestView ────────────────────────────────────────────

    override fun navigateToVerify(email: String) {
        startActivity(
            Intent(this, ForgotVerifyActivity::class.java).apply {
                putExtra(ForgotVerifyActivity.EXTRA_EMAIL, email)
            }
        )
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnSend.isEnabled = false
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
        btnSend.isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
