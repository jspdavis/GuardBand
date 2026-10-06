package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.ui.loading.LoadingActivity
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch

/**
 * Sign-Up final screen — consent, and the commit.
 *
 * Outside the step indicator by design, so it carries a bare back arrow
 * instead of the shared header. Receives everything the wizard collected and
 * is the only screen that writes anything.
 *
 * View ids: btn_signup_back, tv_signup_consent_title,
 * tv_signup_consent_subtitle, cb_signup_consent, btn_signup_consent_create,
 * btn_signup_consent_retry, progress_signup_consent.
 *
 * Retry calls the same handler with the same arguments as Create account — the
 * ViewModel's `accountCreated` guard is what makes the second call resume at
 * the write instead of creating a second account.
 *
 * Flow: SignUpConsentActivity → LoadingActivity → HomeActivity
 */
class SignUpConsentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_CONTACTS = "extra_contacts"
        const val EXTRA_COMPLETE_PROFILE = "extra_complete_profile"
    }

    private lateinit var checkBox: MaterialCheckBox
    private lateinit var btnCreate: Button
    private lateinit var btnRetry: Button
    private lateinit var progressBar: ProgressBar

    private val viewModel: SignUpConsentViewModel by viewModels { SignUpConsentViewModel.Factory }

    private var userName: String = ""
    private var email: String = ""
    private var password: String = ""
    private var completeProfile: Boolean = false
    private var contacts: List<EmergencyContact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_consent)

        userName        = intent.getStringExtra(EXTRA_NAME) ?: ""
        email           = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        password        = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        completeProfile = intent.getBooleanExtra(EXTRA_COMPLETE_PROFILE, false)
        contacts        = readContacts()

        checkBox    = findViewById(R.id.cb_signup_consent)
        btnCreate   = findViewById(R.id.btn_signup_consent_create)
        btnRetry    = findViewById(R.id.btn_signup_consent_retry)
        progressBar = findViewById(R.id.progress_signup_consent)

        findViewById<View>(R.id.btn_signup_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onConsentChanged(isChecked)
        }

        btnCreate.setOnClickListener { submit() }
        // Deliberately the same call: see the class KDoc.
        btnRetry.setOnClickListener { submit() }

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

    private fun submit() = viewModel.onCreateAccountClicked(
        name = userName,
        email = email,
        password = password,
        contacts = contacts,
        completeProfile = completeProfile
    )

    @Suppress("DEPRECATION")
    private fun readContacts(): List<EmergencyContact> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_CONTACTS, EmergencyContact::class.java)
        } else {
            intent.getParcelableArrayListExtra(EXTRA_CONTACTS)
        } ?: emptyList()

    private fun render(state: SignUpConsentUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        btnCreate.isEnabled = state.consentGiven && !state.isLoading
        btnRetry.isEnabled = !state.isLoading

        btnRetry.visibility = if (state.canRetry) View.VISIBLE else View.GONE
        // Create account would only try to make it a second time, which is the
        // dead end Retry exists to avoid.
        btnCreate.visibility = if (state.canRetry) View.GONE else View.VISIBLE
    }

    private fun handleEvent(event: SignUpConsentEvent) {
        when (event) {
            is SignUpConsentEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            SignUpConsentEvent.NavigateToLoadingHome ->
                startActivity(
                    Intent(this, LoadingActivity::class.java).apply {
                        putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
        }
    }
}
