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
 * Sign-Up Step 1 of 3 — the account credentials.
 *
 * View ids: et_signup_email, et_signup_password, et_signup_confirm_password,
 * btn_signup_account_next, til_signup_email, til_signup_password,
 * til_signup_confirm_password, tv_signup_account_title,
 * tv_signup_account_subtitle. The back arrow and step indicator come from the
 * shared header — see [SignUpStepHeader].
 *
 * **The password travels to the Consent screen as an Intent extra**, because
 * nothing is created until the user consents. That keeps an abandoned sign-up
 * from leaving an account behind, at the cost of the credential living in
 * three Intents on the way. The extras only ever address components inside
 * this app, so they do not leave the process — but nothing may log them, and
 * nothing else should read them.
 *
 * Flow: SignUpAccountActivity → SignUpNameActivity
 */
class SignUpAccountActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnNext: Button

    private val viewModel: SignUpAccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_account)

        SignUpStepHeader.bind(this, step = 1)

        etEmail           = findViewById(R.id.et_signup_email)
        etPassword        = findViewById(R.id.et_signup_password)
        etConfirmPassword = findViewById(R.id.et_signup_confirm_password)
        btnNext           = findViewById(R.id.btn_signup_account_next)

        btnNext.setOnClickListener {
            viewModel.onNextClicked(
                email = etEmail.text.toString(),
                password = etPassword.text.toString(),
                confirmPassword = etConfirmPassword.text.toString()
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: SignUpAccountEvent) {
        when (event) {
            is SignUpAccountEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()

            is SignUpAccountEvent.NavigateToName ->
                startActivity(
                    Intent(this, SignUpNameActivity::class.java).apply {
                        putExtra(SignUpNameActivity.EXTRA_EMAIL, event.email)
                        putExtra(SignUpNameActivity.EXTRA_PASSWORD, event.password)
                    }
                )
        }
    }
}
