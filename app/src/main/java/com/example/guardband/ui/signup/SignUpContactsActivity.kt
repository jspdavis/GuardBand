package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
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
 * Receives: [EXTRA_NAME], [EXTRA_LOCATION] from SignUpLocationActivity.
 * View ids: et_signup_contacts_email, et_signup_contacts_password,
 * et_contact_name, et_contact_phone, et_contact_relationship,
 * btn_signup_contacts_submit, progress_signup_contacts, and the matching
 * til_* wrappers.
 *
 * Flow: SignUpContactsActivity → LoadingActivity → HomeActivity
 *
 * A single contact block is shown; list expansion can be implemented in a
 * follow-up iteration.
 */
class SignUpContactsActivity : AppCompatActivity() {

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

    private val viewModel: SignUpContactsViewModel by viewModels { SignUpContactsViewModel.Factory }
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

        btnSubmit.setOnClickListener {
            viewModel.onSubmitClicked(
                name                = userName,
                location            = userLocation,
                email               = etEmail.text.toString(),
                password            = etPassword.text.toString(),
                contactName         = etContactName.text.toString(),
                contactPhone        = etContactPhone.text.toString(),
                contactRelationship = etContactRelationship.text.toString()
            )
        }

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

    private fun render(state: SignUpContactsUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !state.isLoading
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
