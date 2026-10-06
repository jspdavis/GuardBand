package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.loading.LoadingActivity
import kotlinx.coroutines.launch

/**
 * Sign-Up Step 3 — user adds an emergency contact and finishes registration.
 *
 * Receives: [EXTRA_NAME] from the name step, and in complete-profile mode
 * [EXTRA_EMAIL] and [EXTRA_COMPLETE_PROFILE] straight from Login.
 *
 * In complete-profile mode (first-time Google sign-in) the account already
 * exists, so the credential section is hidden and only the emergency contact
 * is saved.
 * View ids: et_signup_contacts_email, et_signup_contacts_password,
 * et_contact_name, et_contact_phone, et_contact_relationship,
 * btn_signup_contacts_submit, btn_signup_contacts_retry,
 * progress_signup_contacts, the matching til_* wrappers, and - hidden together
 * in complete-profile mode - til_signup_contacts_email,
 * til_signup_contacts_password, tv_signup_contacts_credentials_label,
 * divider_signup_contacts_credentials.
 *
 * Flow: SignUpContactsActivity → LoadingActivity → HomeActivity
 *
 * Retry appears when the account was created but its profile write failed. It
 * calls the same handler with the same arguments as Submit - the ViewModel's
 * own guard is what makes the second call resume at the write instead of
 * creating a second account.
 *
 * A single contact block is shown, and it is optional: the Contacts tab is
 * where the user is asked to reach the minimum of three.
 */
class SignUpContactsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"

        /** The Google address, in complete-profile mode only. */
        const val EXTRA_EMAIL = "extra_email"

        /** True when a Google sign-in already created the account. */
        const val EXTRA_COMPLETE_PROFILE = "extra_complete_profile"
    }

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etContactName: EditText
    private lateinit var etContactPhone: EditText
    private lateinit var etContactRelationship: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnRetry: Button
    private lateinit var progressBar: ProgressBar

    /** Hidden as a group in complete-profile mode. */
    private lateinit var credentialViews: List<View>

    private val viewModel: SignUpContactsViewModel by viewModels { SignUpContactsViewModel.Factory }
    private var userName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_contacts)

        userName = intent.getStringExtra(EXTRA_NAME) ?: ""

        viewModel.setCompleteProfileMode(
            intent.getBooleanExtra(EXTRA_COMPLETE_PROFILE, false)
        )

        etEmail               = findViewById(R.id.et_signup_contacts_email)
        etPassword            = findViewById(R.id.et_signup_contacts_password)
        etContactName         = findViewById(R.id.et_contact_name)
        etContactPhone        = findViewById(R.id.et_contact_phone)
        etContactRelationship = findViewById(R.id.et_contact_relationship)
        btnSubmit             = findViewById(R.id.btn_signup_contacts_submit)
        btnRetry              = findViewById(R.id.btn_signup_contacts_retry)
        progressBar           = findViewById(R.id.progress_signup_contacts)

        credentialViews = listOf<View>(
            findViewById(R.id.til_signup_contacts_email),
            findViewById(R.id.til_signup_contacts_password),
            findViewById<TextView>(R.id.tv_signup_contacts_credentials_label),
            findViewById(R.id.divider_signup_contacts_credentials)
        )

        btnSubmit.setOnClickListener { submit() }
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

    private fun submit() = viewModel.onSubmitClicked(
        name                = userName,
        email               = etEmail.text.toString(),
        password            = etPassword.text.toString(),
        contactName         = etContactName.text.toString(),
        contactPhone        = etContactPhone.text.toString(),
        contactRelationship = etContactRelationship.text.toString()
    )

    private fun render(state: SignUpContactsUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !state.isLoading

        btnRetry.visibility = if (state.canRetry) View.VISIBLE else View.GONE
        btnRetry.isEnabled = !state.isLoading
        // Submit would only try to create the account a second time, which is
        // the dead end the retry exists to avoid.
        btnSubmit.visibility = if (state.canRetry) View.GONE else View.VISIBLE

        // GONE, not INVISIBLE: the section must not leave a gap in the
        // LinearLayout when the account already exists.
        val credentialsVisibility = if (state.completeProfile) View.GONE else View.VISIBLE
        credentialViews.forEach { it.visibility = credentialsVisibility }
    }

    private fun handleEvent(event: SignUpContactsEvent) {
        when (event) {
            is SignUpContactsEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            SignUpContactsEvent.NavigateToLoadingHome ->
                startActivity(
                    Intent(this, LoadingActivity::class.java).apply {
                        putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
        }
    }
}
