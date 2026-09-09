package com.example.guardband.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R

/**
 * Sign-Up Step 1 — user enters their full name.
 *
 * Flow: SignUpNameActivity → SignUpLocationActivity
 */
class SignUpNameActivity : AppCompatActivity(), AuthContract.SignUpNameView {

    private lateinit var etName: EditText
    private lateinit var btnNext: Button
    private lateinit var progressBar: ProgressBar

    private val presenter = SignUpNamePresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_name)

        etName      = findViewById(R.id.et_signup_name)
        btnNext     = findViewById(R.id.btn_signup_name_next)
        progressBar = findViewById(R.id.progress_signup_name)

        presenter.attachView(this)

        btnNext.setOnClickListener {
            presenter.onNextClicked(etName.text.toString())
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── AuthContract.SignUpNameView ───────────────────────────────────────────

    override fun navigateToSignUpLocation(name: String) {
        startActivity(
            Intent(this, SignUpLocationActivity::class.java).apply {
                putExtra(SignUpLocationActivity.EXTRA_NAME, name)
            }
        )
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnNext.isEnabled = false
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
        btnNext.isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
