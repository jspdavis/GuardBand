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
import com.example.guardband.ui.loading.LoadingActivity

/**
 * Sign-Up Step 3 — user adds emergency contacts and finishes registration.
 *
 * Receives: [EXTRA_NAME], [EXTRA_LOCATION] from SignUpLocationActivity.
 * Flow: SignUpContactsActivity → LoadingActivity → DashboardActivity
 *
 * NOTE: Contact list management (add/remove rows) is intentionally minimal for
 * this MVP wireframe pass. A single contact block is shown; list expansion can
 * be implemented in a follow-up iteration.
 */
class SignUpContactsActivity : AppCompatActivity(), AuthContract.SignUpContactsView {

    companion object {
        const val EXTRA_NAME     = "extra_name"
        const val EXTRA_LOCATION = "extra_location"
    }

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etContactName: EditText
    private lateinit var etContactPhone: EditText
    private lateinit var etContactRelationship: EditText
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar

    private val presenter = SignUpContactsPresenter()
    private var userName: String = ""
    private var userLocation: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_contacts)

        userName     = intent.getStringExtra(EXTRA_NAME)     ?: ""
        userLocation = intent.getStringExtra(EXTRA_LOCATION) ?: ""

        etEmail               = findViewById(R.id.et_signup_contacts_email)
        etPassword            = findViewById(R.id.et_signup_contacts_password)
        etContactName         = findViewById(R.id.et_contact_name)
        etContactPhone        = findViewById(R.id.et_contact_phone)
        etContactRelationship = findViewById(R.id.et_contact_relationship)
        btnSubmit             = findViewById(R.id.btn_signup_contacts_submit)
        progressBar           = findViewById(R.id.progress_signup_contacts)

        presenter.attachView(this)

        btnSubmit.setOnClickListener {
            presenter.onSubmitClicked(
                name     = userName,
                location = userLocation,
                email    = etEmail.text.toString(),
                password = etPassword.text.toString()
            )
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── AuthContract.SignUpContactsView ───────────────────────────────────────

    override fun navigateToDashboard() {
        startActivity(
            Intent(this, LoadingActivity::class.java).apply {
                putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_DASHBOARD)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
        btnSubmit.isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
