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
 * View ids: et_signup_name, btn_signup_name_next, signup_google_button,
 * progress_signup_name, til_signup_name, tv_signup_name_title,
 * tv_signup_step_name.
 *
 * Flow: SignUpNameActivity → SignUpLocationActivity
 *       SignUpNameActivity → LoginActivity (Google)
 */
class SignUpNameActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var btnNext: Button
    private lateinit var btnGoogle: Button

    private val viewModel: SignUpNameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_name)

        etName  = findViewById(R.id.et_signup_name)
        btnNext   = findViewById(R.id.btn_signup_name_next)
        btnGoogle = findViewById(R.id.signup_google_button)

        btnNext.setOnClickListener {
            viewModel.onNextClicked(etName.text.toString())
        }

        btnGoogle.setOnClickListener { viewModel.onGoogleSignInClicked() }

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

            is SignUpNameEvent.NavigateToLocation ->
                startActivity(
                    Intent(this, SignUpLocationActivity::class.java).apply {
                        putExtra(SignUpLocationActivity.EXTRA_NAME, event.name)
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
