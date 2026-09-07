package com.example.guardband.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.data.ContactModel
import com.example.guardband.ui.auth.LoginActivity

/**
 * Main dashboard — shown after a successful login or sign-up.
 *
 * Displays a welcome header, protection status badge, and a dynamically
 * built list of emergency contacts loaded from [MockRepository].
 *
 * Flow: DashboardActivity → (Logout) → LoginActivity (back-stack cleared)
 */
class DashboardActivity : AppCompatActivity(), DashboardContract.View {

    companion object {
        /** Optional: pass a display name through intent extras. */
        const val EXTRA_USER_NAME = "extra_user_name"
    }

    private lateinit var tvWelcome: TextView
    private lateinit var btnLogout: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var contactsContainer: LinearLayout

    private lateinit var presenter: DashboardPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvWelcome         = findViewById(R.id.tv_dashboard_welcome)
        btnLogout         = findViewById(R.id.btn_dashboard_logout)
        progressBar       = findViewById(R.id.progress_dashboard)
        contactsContainer = findViewById(R.id.ll_dashboard_contacts)

        val userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""
        presenter = DashboardPresenter(userName)
        presenter.attachView(this)

        btnLogout.setOnClickListener { presenter.onLogoutClicked() }

        presenter.loadDashboard()
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── DashboardContract.View ────────────────────────────────────────────────

    override fun showUserName(name: String) {
        tvWelcome.text = getString(R.string.label_dashboard_welcome, name)
    }

    override fun showContacts(contacts: List<ContactModel>) {
        contactsContainer.removeAllViews()

        if (contacts.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No emergency contacts added yet."
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 8, 0, 8)
            }
            contactsContainer.addView(empty)
            return
        }

        contacts.forEach { contact ->
            val row = TextView(this).apply {
                text = "• ${contact.name}  |  ${contact.phoneNumber}  |  ${contact.relationship}"
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, 10, 0, 10)
            }
            contactsContainer.addView(row)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(getColor(R.color.input_bg))
            }
            contactsContainer.addView(divider)
        }
    }

    override fun navigateToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    // ── BaseView ──────────────────────────────────────────────────────────────

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
