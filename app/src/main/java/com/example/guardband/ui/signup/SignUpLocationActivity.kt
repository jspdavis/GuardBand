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
 * Receives: [EXTRA_NAME] from SignUpNameActivity.
 * Flow: SignUpLocationActivity → SignUpContactsActivity
 */
class SignUpLocationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "extra_name"
    }

    private lateinit var etLocation: EditText
    private lateinit var btnNext: Button

    private val viewModel: SignUpLocationViewModel by viewModels()
    private var userName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_location)

        userName   = intent.getStringExtra(EXTRA_NAME) ?: ""
        etLocation = findViewById(R.id.et_signup_location)
        btnNext    = findViewById(R.id.btn_signup_location_next)

        btnNext.setOnClickListener {
            viewModel.onNextClicked(
                name     = userName,
                location = etLocation.text.toString()
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
                    }
                )
        }
    }
}
