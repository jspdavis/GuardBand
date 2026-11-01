package com.example.guardband.ui.signup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.example.guardband.data.model.Role

import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch

/**
 * Sign-Up final screen — consent, and the commit.
 *
 * Flow: SignUpConsentActivity → WelcomeActivity → LoadingActivity → HomeActivity
 */
class SignUpConsentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_CONTACTS = "extra_contacts"
        const val EXTRA_ROLE = "extra_role"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_COMPLETE_PROFILE = "extra_complete_profile"

        private const val DISABLED_ALPHA = 0.5f
    }

    private lateinit var checkBox: MaterialCheckBox
    private lateinit var btnCreate: Button
    private lateinit var btnRetry: Button
    private lateinit var btnPrivacy: Button
    private lateinit var progressBar: ProgressBar

    private val viewModel: SignUpConsentViewModel by viewModels { SignUpConsentViewModel.Factory }

    private var userName: String = ""
    private var email: String = ""
    private var password: String = ""
    private var role: Role = Role.DEPENDENT
    private var ownPhone: String = ""
    private var completeProfile: Boolean = false
    private var contacts: List<EmergencyContact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_consent)

        userName        = intent.getStringExtra(EXTRA_NAME) ?: ""
        email           = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        password        = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val roleStr     = intent.getStringExtra(EXTRA_ROLE)
        role            = Role.fromWire(roleStr) ?: Role.DEPENDENT
        ownPhone        = intent.getStringExtra(EXTRA_PHONE) ?: ""
        completeProfile = intent.getBooleanExtra(EXTRA_COMPLETE_PROFILE, false)
        contacts        = readContacts()

        checkBox    = findViewById(R.id.cb_signup_consent)
        btnCreate   = findViewById(R.id.btn_signup_consent_create)
        btnRetry    = findViewById(R.id.btn_signup_consent_retry)
        btnPrivacy  = findViewById(R.id.btn_signup_consent_privacy)
        progressBar = findViewById(R.id.progress_signup_consent)

        findViewById<View>(R.id.btn_signup_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onConsentChanged(isChecked)
        }

        btnCreate.setOnClickListener { submit() }
        btnPrivacy.setOnClickListener { viewModel.onPrivacyNoticeClicked() }
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
        completeProfile = completeProfile,
        role = role,
        ownPhone = ownPhone
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
        btnCreate.alpha = if (btnCreate.isEnabled) 1f else DISABLED_ALPHA
        btnRetry.alpha = if (btnRetry.isEnabled) 1f else DISABLED_ALPHA

        btnRetry.visibility = if (state.canRetry) View.VISIBLE else View.GONE
        btnCreate.visibility = if (state.canRetry) View.GONE else View.VISIBLE
    }

    private fun openPrivacyNotice(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onPrivacyNoticeUnavailable()
        }
    }

    private fun handleEvent(event: SignUpConsentEvent) {
        when (event) {
            is SignUpConsentEvent.OpenPrivacyNotice -> openPrivacyNotice(event.url)

            is SignUpConsentEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            SignUpConsentEvent.NavigateToWelcome ->
                startActivity(
                    Intent(this, WelcomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
        }
    }
}
