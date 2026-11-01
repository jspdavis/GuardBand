package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.data.model.Role
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * Sign-Up Step 1 of 3 — Role selection, own phone, and account credentials.
 *
 * Flow: SignUpAccountActivity → SignUpNameActivity
 */
class SignUpAccountActivity : AppCompatActivity() {

    private lateinit var cardGuardian: MaterialCardView
    private lateinit var cardDependent: MaterialCardView
    private lateinit var rbGuardian: RadioButton
    private lateinit var rbDependent: RadioButton
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnNext: Button

    private var selectedRole: Role? = null

    private val viewModel: SignUpAccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_account)

        SignUpStepHeader.bind(this, step = 1)

        cardGuardian      = findViewById(R.id.card_role_guardian)
        cardDependent     = findViewById(R.id.card_role_dependent)
        rbGuardian        = findViewById(R.id.rb_role_guardian)
        rbDependent       = findViewById(R.id.rb_role_dependent)
        etPhone           = findViewById(R.id.et_signup_phone)
        etEmail           = findViewById(R.id.et_signup_email)
        etPassword        = findViewById(R.id.et_signup_password)
        etConfirmPassword = findViewById(R.id.et_signup_confirm_password)
        btnNext           = findViewById(R.id.btn_signup_account_next)

        fun selectRole(role: Role) {
            selectedRole = role
            val isGuardian = role == Role.GUARDIAN
            rbGuardian.isChecked = isGuardian
            rbDependent.isChecked = !isGuardian

            val strokeActive = getColor(R.color.brand_primary)
            val strokeInactive = getColor(R.color.stroke_muted)
            val strokeWidthActive = resources.getDimensionPixelSize(R.dimen.alert_card_panic_stroke).coerceAtLeast(4)
            val strokeWidthInactive = 2

            cardGuardian.strokeColor = if (isGuardian) strokeActive else strokeInactive
            cardGuardian.strokeWidth = if (isGuardian) strokeWidthActive else strokeWidthInactive

            cardDependent.strokeColor = if (!isGuardian) strokeActive else strokeInactive
            cardDependent.strokeWidth = if (!isGuardian) strokeWidthActive else strokeWidthInactive
        }

        cardGuardian.setOnClickListener { selectRole(Role.GUARDIAN) }
        rbGuardian.setOnClickListener { selectRole(Role.GUARDIAN) }
        cardDependent.setOnClickListener { selectRole(Role.DEPENDENT) }
        rbDependent.setOnClickListener { selectRole(Role.DEPENDENT) }

        // Default: explicitly require user tap, or preselect GUARDIAN
        selectRole(Role.GUARDIAN)

        btnNext.setOnClickListener {
            if (selectedRole == null) {
                Toast.makeText(this, R.string.error_role_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.onNextClicked(
                email = etEmail.text.toString(),
                password = etPassword.text.toString(),
                confirmPassword = etConfirmPassword.text.toString(),
                role = selectedRole,
                phone = etPhone.text.toString()
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
                        putExtra(SignUpNameActivity.EXTRA_ROLE, event.role.name)
                        putExtra(SignUpNameActivity.EXTRA_PHONE, event.ownPhone)
                    }
                )
        }
    }
}
