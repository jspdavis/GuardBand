package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.data.model.Role
import com.google.android.material.card.MaterialCardView

/**
 * Role-selection wizard step with mutual alignment guidance (T11).
 */
class SignUpRoleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COMPLETE_PROFILE = "extra_complete_profile"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
    }

    private lateinit var cardGuardian: MaterialCardView
    private lateinit var cardDependent: MaterialCardView
    private lateinit var rbGuardian: RadioButton
    private lateinit var rbDependent: RadioButton
    private lateinit var btnNext: Button

    private var selectedRole: Role? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_role)

        SignUpStepHeader.bind(this, step = 1)

        cardGuardian = findViewById(R.id.card_role_step_guardian)
        cardDependent = findViewById(R.id.card_role_step_dependent)
        rbGuardian = findViewById(R.id.rb_role_step_guardian)
        rbDependent = findViewById(R.id.rb_role_step_dependent)
        btnNext = findViewById(R.id.btn_role_next)

        fun selectRole(role: Role) {
            selectedRole = role
            val isGuardian = role == Role.GUARDIAN
            rbGuardian.isChecked = isGuardian
            rbDependent.isChecked = !isGuardian

            val strokeActive = getColor(R.color.brand_primary)
            val strokeInactive = getColor(R.color.stroke_muted)
            val strokeWidthActive = resources.getDimensionPixelSize(R.dimen.alert_card_panic_stroke).coerceAtLeast(4)

            cardGuardian.strokeColor = if (isGuardian) strokeActive else strokeInactive
            cardGuardian.strokeWidth = if (isGuardian) strokeWidthActive else 2

            cardDependent.strokeColor = if (!isGuardian) strokeActive else strokeInactive
            cardDependent.strokeWidth = if (!isGuardian) strokeWidthActive else 2
        }

        cardGuardian.setOnClickListener { selectRole(Role.GUARDIAN) }
        rbGuardian.setOnClickListener { selectRole(Role.GUARDIAN) }
        cardDependent.setOnClickListener { selectRole(Role.DEPENDENT) }
        rbDependent.setOnClickListener { selectRole(Role.DEPENDENT) }

        selectRole(Role.GUARDIAN)

        btnNext.setOnClickListener {
            val role = selectedRole
            if (role == null) {
                Toast.makeText(this, R.string.error_role_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val completeProfile = intent.getBooleanExtra(EXTRA_COMPLETE_PROFILE, false)
            if (completeProfile) {
                startActivity(
                    Intent(this, SignUpContactsActivity::class.java).apply {
                        putExtra(SignUpContactsActivity.EXTRA_COMPLETE_PROFILE, true)
                        putExtra(SignUpContactsActivity.EXTRA_ROLE, role.name)
                        putExtra(SignUpContactsActivity.EXTRA_NAME, intent.getStringExtra(EXTRA_NAME) ?: "")
                        putExtra(SignUpContactsActivity.EXTRA_EMAIL, intent.getStringExtra(EXTRA_EMAIL) ?: "")
                    }
                )
            } else {
                startActivity(
                    Intent(this, SignUpAccountActivity::class.java)
                )
            }
        }
    }
}
