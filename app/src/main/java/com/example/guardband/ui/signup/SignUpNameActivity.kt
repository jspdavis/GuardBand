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
 * Sign-Up Step 2 — user enters their full name.
 *
 * Receives [EXTRA_EMAIL], [EXTRA_PASSWORD], [EXTRA_PHONE] and passes them on.
 *
 * Flow: SignUpNameActivity → SignUpContactsActivity
 */
class SignUpNameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_PHONE = "extra_phone"
    }

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var btnNext: Button

    private val viewModel: SignUpNameViewModel by viewModels()

    private var email: String = ""
    private var password: String = ""
    private var phone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_name)

        SignUpStepHeader.bind(this, step = 2)

        email    = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        phone    = intent.getStringExtra(EXTRA_PHONE) ?: ""

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
                        putExtra(SignUpContactsActivity.EXTRA_PHONE, phone)
                    }
                )
        }
    }
}
