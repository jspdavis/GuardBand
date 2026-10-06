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
import com.example.guardband.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Sign-Up Step 1 — user enters their full name.
 *
 * View ids: et_signup_first_name, et_signup_last_name, btn_signup_name_next,
 * progress_signup_name, til_signup_first_name, til_signup_last_name,
 * tv_signup_name_title, tv_signup_name_subtitle. The back arrow and the step
 * indicator come from the shared header — see [SignUpStepHeader].
 *
 * Step 2 of 3 in the revamped flow (Account → Name → Contacts → Consent).
 *
 * Receives [EXTRA_EMAIL] and [EXTRA_PASSWORD] from step 1 and passes them on
 * untouched — it neither reads nor validates them.
 *
 * The screen has no Google button: that path lives on Login, which offers
 * "Continue with Google" and routes a first-time user through the
 * complete-profile steps. [SignUpNameEvent.NavigateToGoogleSignIn] and
 * [SignUpNameViewModel.onGoogleSignInClicked] are therefore currently
 * unreachable, kept for the sign-up revamp rather than deleted.
 *
 * Flow: SignUpNameActivity → SignUpContactsActivity
 */
class SignUpNameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_PASSWORD = "extra_password"
    }

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var btnNext: Button

    private val viewModel: SignUpNameViewModel by viewModels()

    private var email: String = ""
    private var password: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_name)

        SignUpStepHeader.bind(this, step = 2)

        email    = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""

        etFirstName = findViewById(R.id.et_signup_first_name)
        etLastName  = findViewById(R.id.et_signup_last_name)
        btnNext     = findViewById(R.id.btn_signup_name_next)

        btnNext.setOnClickListener {
            viewModel.onNextClicked(
                firstName = etFirstName.text.toString(),
                lastName = etLastName.text.toString(),
                email = email,
                password = password
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: SignUpNameEvent) {
        when (event) {
            is SignUpNameEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            is SignUpNameEvent.NavigateToContacts ->
                startActivity(
                    Intent(this, SignUpContactsActivity::class.java).apply {
                        putExtra(SignUpContactsActivity.EXTRA_NAME, event.name)
                        putExtra(SignUpContactsActivity.EXTRA_EMAIL, event.email)
                        putExtra(SignUpContactsActivity.EXTRA_PASSWORD, event.password)
                    }
                )

            // finish() so Back from Login does not land back on a half-filled
            // sign-up step the user has abandoned.
            SignUpNameEvent.NavigateToGoogleSignIn -> {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
}
