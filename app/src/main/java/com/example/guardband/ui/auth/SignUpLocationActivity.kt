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
 * Sign-Up Step 2 — user sets their city / region.
 *
 * Receives: [EXTRA_NAME] from SignUpNameActivity.
 * Flow: SignUpLocationActivity → SignUpContactsActivity
 */
class SignUpLocationActivity : AppCompatActivity(), AuthContract.SignUpLocationView {

    companion object {
        const val EXTRA_NAME = "extra_name"
    }

    private lateinit var etLocation: EditText
    private lateinit var btnNext: Button
    private lateinit var progressBar: ProgressBar

    private val presenter = SignUpLocationPresenter()
    private var userName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_location)

        userName    = intent.getStringExtra(EXTRA_NAME) ?: ""
        etLocation  = findViewById(R.id.et_signup_location)
        btnNext     = findViewById(R.id.btn_signup_location_next)
        progressBar = findViewById(R.id.progress_signup_location)

        presenter.attachView(this)

        btnNext.setOnClickListener {
            presenter.onNextClicked(
                name     = userName,
                location = etLocation.text.toString()
            )
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── AuthContract.SignUpLocationView ───────────────────────────────────────

    override fun navigateToSignUpContacts(name: String, location: String) {
        startActivity(
            Intent(this, SignUpContactsActivity::class.java).apply {
                putExtra(SignUpContactsActivity.EXTRA_NAME, name)
                putExtra(SignUpContactsActivity.EXTRA_LOCATION, location)
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
