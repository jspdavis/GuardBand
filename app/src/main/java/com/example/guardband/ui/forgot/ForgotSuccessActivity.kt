package com.example.guardband.ui.forgot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.ui.auth.LoginActivity

/**
 * Forgot Password — Step 4.
 * Confirms that the password was changed successfully.
 *
 * Flow: ForgotSuccessActivity → LoginActivity (clears forgot back-stack)
 */
class ForgotSuccessActivity : AppCompatActivity(), ForgotContract.SuccessView {

    private lateinit var btnBackToLogin: Button

    private val presenter = ForgotSuccessPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_success)

        btnBackToLogin = findViewById(R.id.btn_forgot_success_login)

        presenter.attachView(this)

        btnBackToLogin.setOnClickListener {
            presenter.onBackToLoginClicked()
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── ForgotContract.SuccessView ────────────────────────────────────────────

    override fun navigateToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() { /* no async op on this screen */ }
    override fun hideLoading() { /* no async op on this screen */ }
    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
