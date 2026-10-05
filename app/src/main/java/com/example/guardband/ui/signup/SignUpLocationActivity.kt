package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import kotlinx.coroutines.launch

/**
 * Sign-Up Step 2 — user sets their city / region.
 *
 * Receives: [EXTRA_NAME] from SignUpNameActivity, or [EXTRA_NAME],
 * [EXTRA_EMAIL] and [EXTRA_COMPLETE_PROFILE] from LoginActivity after a
 * first-time Google sign-in.
 *
 * In complete-profile mode the account already exists, so this is the first
 * step the user sees and the step indicator in the layout no longer matches.
 *
 * Flow: SignUpLocationActivity → SignUpContactsActivity
 */
class SignUpLocationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"

        /** The Google address, in complete-profile mode only. */
        const val EXTRA_EMAIL = "extra_email"

        /**
         * True when a Google sign-in already created the account, so the final
         * step must save a profile instead of registering.
         */
        const val EXTRA_COMPLETE_PROFILE = "extra_complete_profile"
    }

    private lateinit var etLocation: EditText
    private lateinit var btnNext: Button

    private val viewModel: SignUpLocationViewModel by viewModels()
    private var userName: String = ""
    private var userEmail: String = ""
    private var completeProfile: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_location)

        userName        = intent.getStringExtra(EXTRA_NAME) ?: ""
        userEmail       = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        completeProfile = intent.getBooleanExtra(EXTRA_COMPLETE_PROFILE, false)

        etLocation = findViewById(R.id.et_signup_location)
        btnNext    = findViewById(R.id.btn_signup_location_next)

        btnNext.setOnClickListener {
            viewModel.onNextClicked(
                name            = userName,
                location        = etLocation.text.toString(),
                email           = userEmail,
                completeProfile = completeProfile
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: SignUpLocationEvent) {
        when (event) {
            is SignUpLocationEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            is SignUpLocationEvent.NavigateToContacts ->
                startActivity(
                    Intent(this, SignUpContactsActivity::class.java).apply {
                        putExtra(SignUpContactsActivity.EXTRA_NAME, event.name)
                        putExtra(SignUpContactsActivity.EXTRA_LOCATION, event.location)
                        putExtra(SignUpContactsActivity.EXTRA_EMAIL, event.email)
                        putExtra(
                            SignUpContactsActivity.EXTRA_COMPLETE_PROFILE,
                            event.completeProfile
                        )
                    }
                )
        }
    }
}
