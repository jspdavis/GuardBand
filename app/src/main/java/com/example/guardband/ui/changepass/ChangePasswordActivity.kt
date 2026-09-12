package com.example.guardband.ui.changepass

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.guardband.R
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.ui.dashboard.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ChangePasswordActivity : AppCompatActivity(), ChangePasswordContract.View {

    private lateinit var tilCurrentPassword: TextInputLayout
    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnChangePassword: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var tvRule1: TextView
    private lateinit var tvRule2: TextView
    private lateinit var tvRule3: TextView
    private lateinit var tvRule4: TextView
    private lateinit var tvRule5: TextView

    private val presenter = ChangePasswordPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        bindViews()
        presenter.attachView(this)
        setupListeners()
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    private fun bindViews() {
        tilCurrentPassword = findViewById(R.id.tilCurrentPassword)
        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        tilNewPassword = findViewById(R.id.tilNewPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnCancel = findViewById(R.id.btnCancel)
        tvRule1 = findViewById(R.id.tvRule1)
        tvRule2 = findViewById(R.id.tvRule2)
        tvRule3 = findViewById(R.id.tvRule3)
        tvRule4 = findViewById(R.id.tvRule4)
        tvRule5 = findViewById(R.id.tvRule5)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            presenter.onCancelClicked()
        }

        btnCancel.setOnClickListener {
            presenter.onCancelClicked()
        }

        btnChangePassword.setOnClickListener {
            // Clear previous errors when user tries again.
            showCurrentPasswordError(null)
            showNewPasswordError(null)
            showConfirmPasswordError(null)

            presenter.onChangePasswordClicked(
                etCurrentPassword.text?.toString().orEmpty(),
                etNewPassword.text?.toString().orEmpty(),
                etConfirmPassword.text?.toString().orEmpty()
            )
        }

        // Live password criteria feedback — relay every keystroke to the presenter.
        val passwordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                presenter.onNewPasswordTyped(
                    etNewPassword.text?.toString().orEmpty(),
                    etConfirmPassword.text?.toString().orEmpty()
                )
            }
        }
        etNewPassword.addTextChangedListener(passwordWatcher)
        etConfirmPassword.addTextChangedListener(passwordWatcher)

        // 🔍 DIAGNOSTIC: Track focus changes to detect unexpected focus stealing
        etNewPassword.setOnFocusChangeListener { view, hasFocus ->
            Log.d(TAG_FOCUS, "═══ etNewPassword focus change ═══")
            Log.d(TAG_FOCUS, "hasFocus: $hasFocus")
            Log.d(TAG_FOCUS, "Stack trace (first 10):")
            Thread.currentThread().stackTrace.take(10).forEach {
                Log.d(TAG_FOCUS, "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
            }
            Log.d(TAG_FOCUS, "════════════════════════════════")
        }

        etConfirmPassword.setOnFocusChangeListener { view, hasFocus ->
            Log.d(TAG_FOCUS, "═══ etConfirmPassword focus change ═══")
            Log.d(TAG_FOCUS, "hasFocus: $hasFocus")
            Log.d(TAG_FOCUS, "Stack trace (first 10):")
            Thread.currentThread().stackTrace.take(10).forEach {
                Log.d(TAG_FOCUS, "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
            }
            Log.d(TAG_FOCUS, "════════════════════════════════")
        }
    }

    // ── ChangePasswordContract.View ───────────────────────────────────────────

    override fun showCurrentPasswordError(message: String?) {
        tilCurrentPassword.error = message
    }

    override fun showNewPasswordError(message: String?) {
        tilNewPassword.error = message
    }

    override fun showConfirmPasswordError(message: String?) {
        tilConfirmPassword.error = message
    }

    override fun updatePasswordCriteria(rules: ValidationResult.PasswordRules, hasTyped: Boolean) {
        bindRule(tvRule1, "At least 8 characters", rules.minLength, hasTyped)
        bindRule(tvRule2, "At least 1 uppercase letter", rules.hasUppercase, hasTyped)
        bindRule(tvRule3, "At least 1 lowercase letter", rules.hasLowercase, hasTyped)
        bindRule(
            tvRule4,
            "At least 1 number or special character",
            rules.hasNumberOrSpecial,
            hasTyped
        )
        bindRule(tvRule5, "Passwords match", rules.passwordsMatch, hasTyped)
    }

    private fun bindRule(tv: TextView, label: String, met: Boolean, hasTyped: Boolean) {
        val icon = when {
            !hasTyped -> "☐"
            met -> "☑"
            else -> "☒"
        }
        tv.text = "$icon $label"
        tv.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    !hasTyped -> R.color.rule_neutral
                    met -> R.color.rule_met
                    else -> R.color.rule_unmet
                }
            )
        )
    }

    override fun navigateToDashboard() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading() {
        btnChangePassword.isEnabled = false
        btnCancel.isEnabled = false
    }

    override fun hideLoading() {
        btnChangePassword.isEnabled = true
        btnCancel.isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG_FOCUS = "ChangePasswordFocus"
    }
}
