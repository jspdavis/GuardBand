package com.example.guardband.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Main dashboard — shown after a successful login or sign-up.
 *
 * Displays a welcome header, protection status badge, and a dynamically
 * built list of emergency contacts.
 *
 * Flow: DashboardActivity → (Logout) → LoginActivity (back-stack cleared)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var btnLogout: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var contactsContainer: LinearLayout

    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvWelcome         = findViewById(R.id.tv_dashboard_welcome)
        btnLogout         = findViewById(R.id.btn_dashboard_logout)
        progressBar       = findViewById(R.id.progress_dashboard)
        contactsContainer = findViewById(R.id.ll_dashboard_contacts)

        btnLogout.setOnClickListener { viewModel.onLogoutClicked() }

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

    private fun render(state: DashboardUiState) {
        tvWelcome.text = getString(R.string.label_dashboard_welcome, state.userName)
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        // As before, the list (or its empty state) only appears once loading finishes.
        if (!state.isLoading) showContacts(state.contacts)
    }

    private fun showContacts(contacts: List<EmergencyContact>) {
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

    private fun handleEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.NavigateToLogin ->
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
        }
    }
}
