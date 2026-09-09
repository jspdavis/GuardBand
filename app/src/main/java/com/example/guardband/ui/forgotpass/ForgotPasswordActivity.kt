package com.example.guardband.ui.forgotpass

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.guardband.R
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.ui.login.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ForgotPasswordActivity : AppCompatActivity(), ForgotPasswordContract.View {

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var etEmail: TextInputEditText
    private lateinit var etDigit1: EditText
    private lateinit var etDigit2: EditText
    private lateinit var etDigit3: EditText
    private lateinit var etDigit4: EditText
    private lateinit var etDigit5: EditText
    private lateinit var tvTimer: TextView
    private lateinit var tvOtpSubtitle: TextView
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var tvNewPasswordError: TextView
    private lateinit var tvConfirmPasswordError: TextView
    private lateinit var tvRule1: TextView
    private lateinit var tvRule2: TextView
    private lateinit var tvRule3: TextView
    private lateinit var tvRule4: TextView
    private lateinit var tvRule5: TextView
    private lateinit var btnSubmit: MaterialButton

    private val otpFields = mutableListOf<EditText>()
    private val presenter = ForgotPasswordPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        bindViews()
        presenter.attachView(this)
        setupOtpAutoFocus()
        setupListeners()
        showStep(1)
    }

    override fun onDestroy() {
        presenter.stopOtpTimer()
        presenter.detachView()
        super.onDestroy()
    }

    private fun bindViews() {
        viewFlipper = findViewById(R.id.viewFlipper)
        etEmail = findViewById(R.id.etEmail)
        etDigit1 = findViewById(R.id.etDigit1)
        etDigit2 = findViewById(R.id.etDigit2)
        etDigit3 = findViewById(R.id.etDigit3)
        etDigit4 = findViewById(R.id.etDigit4)
        etDigit5 = findViewById(R.id.etDigit5)
        tvTimer = findViewById(R.id.tvTimer)
        tvOtpSubtitle = findViewById(R.id.tvOtpSubtitle)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tilNewPassword = findViewById(R.id.tilNewPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError)
        tvConfirmPasswordError = findViewById(R.id.tvConfirmPasswordError)
        tvRule1 = findViewById(R.id.tvRule1)
        tvRule2 = findViewById(R.id.tvRule2)
        tvRule3 = findViewById(R.id.tvRule3)
        tvRule4 = findViewById(R.id.tvRule4)
        tvRule5 = findViewById(R.id.tvRule5)
        btnSubmit = findViewById(R.id.btnSubmit)
        otpFields.addAll(listOf(etDigit1, etDigit2, etDigit3, etDigit4, etDigit5))
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.btnRequestReset).setOnClickListener {
            presenter.onRequestReset(etEmail.text?.toString().orEmpty())
        }
        findViewById<MaterialButton>(R.id.btnCancelStep1).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnBackStep1).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnResendCode).setOnClickListener {
            presenter.onResendCode()
        }
        findViewById<TextView>(R.id.tvChangeEmail).setOnClickListener {
            presenter.onChangeEmail()
        }
        findViewById<ImageView>(R.id.btnBackStep2).setOnClickListener {
            presenter.onChangeEmail()
        }

        findViewById<MaterialButton>(R.id.btnVerifyCode).setOnClickListener {
            presenter.onVerifyOtp(collectOtp())
        }
        findViewById<MaterialButton>(R.id.btnResendOtp).setOnClickListener {
            presenter.onResendOtpFromVerify()
        }
        findViewById<ImageView>(R.id.btnBackStep3).setOnClickListener {
            showStep(2)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                presenter.onPasswordChanged(
                    etNewPassword.text?.toString().orEmpty(),
                    etConfirmPassword.text?.toString().orEmpty()
                )
            }
        }
        etNewPassword.addTextChangedListener(watcher)
        etConfirmPassword.addTextChangedListener(watcher)

        btnSubmit.setOnClickListener {
            presenter.onSubmitNewPassword(
                etNewPassword.text?.toString().orEmpty(),
                etConfirmPassword.text?.toString().orEmpty()
            )
        }
        findViewById<MaterialButton>(R.id.btnCancelCredentials).setOnClickListener {
            presenter.onCancelCredentials()
        }
        findViewById<ImageView>(R.id.btnBackCredentials).setOnClickListener {
            showStep(3)
        }

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            presenter.onLoginClicked()
        }
    }

    private fun setupOtpAutoFocus() {
        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < otpFields.lastIndex) {
                        otpFields[index + 1].requestFocus()
                    }
                }
            })
            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    editText.text.isNullOrEmpty() &&
                    index > 0
                ) {
                    otpFields[index - 1].apply {
                        requestFocus()
                        setText("")
                    }
                    true
                } else false
            }
        }
    }

    private fun collectOtp(): String =
        otpFields.joinToString("") { it.text?.toString().orEmpty() }

    override fun showStep(step: Int) {
        val index = when (step) {
            1 -> 0
            2 -> 1
            3 -> 2
            in 4..7 -> 3
            8 -> 4
            else -> 0
        }
        viewFlipper.displayedChild = index
        if (step == 3) {
            tvOtpSubtitle.text = "Enter OTP (One time password) sent to\n${presenter.getEmail()}"
            etDigit1.requestFocus()
        }
    }

    override fun updateTimer(text: String) {
        tvTimer.text = text
    }

    override fun onTimerFinished() {
        findViewById<MaterialButton>(R.id.btnResendOtp).isEnabled = true
    }

    override fun updatePasswordRules(rules: ValidationResult.PasswordRules, hasTyped: Boolean) {
        bindRule(tvRule1, "Password must be at least 8 characters long.", rules.minLength, hasTyped)
        bindRule(tvRule2, "Password must contain at least one upper case.", rules.hasUppercase, hasTyped)
        bindRule(tvRule3, "One lower case letter.", rules.hasLowercase, hasTyped)
        bindRule(
            tvRule4,
            "Password must contain at least one number or special character.",
            rules.hasNumberOrSpecial,
            hasTyped
        )
        bindRule(tvRule5, "Passwords match.", rules.passwordsMatch, hasTyped)
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

    override fun showRequiredFieldErrors(show: Boolean) {
        tvNewPasswordError.visibility = if (show && etNewPassword.text.isNullOrBlank()) View.VISIBLE else View.GONE
        tvConfirmPasswordError.visibility =
            if (show && etConfirmPassword.text.isNullOrBlank()) View.VISIBLE else View.GONE
        if (show) {
            tvConfirmPasswordError.text = getString(R.string.error_required_field)
            tvNewPasswordError.text = getString(R.string.error_required_field)
        }
    }

    override fun showPasswordMismatchError(show: Boolean) {
        if (show) {
            tvConfirmPasswordError.visibility = View.VISIBLE
            tvConfirmPasswordError.text = getString(R.string.error_passwords_mismatch)
        } else if (tvConfirmPasswordError.text != getString(R.string.error_required_field)) {
            tvConfirmPasswordError.visibility = View.GONE
        }
    }

    override fun setCredentialBorders(valid: Boolean?, error: Boolean) {
        val color = when {
            error -> ContextCompat.getColor(this, R.color.error_red)
            valid == true -> ContextCompat.getColor(this, R.color.brand_primary)
            else -> ContextCompat.getColor(this, R.color.stroke_default)
        }
        tilNewPassword.boxStrokeColor = color
        tilConfirmPassword.boxStrokeColor = color
    }

    override fun showDebugOtp(otp: String) {
        // Helps local testing when email delivery / Functions aren't configured.
        Toast.makeText(this, "Dev OTP: $otp", Toast.LENGTH_LONG).show()
    }

    override fun navigateToLoginCleared() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    override fun showLoading() {
        btnSubmit.isEnabled = false
        findViewById<MaterialButton>(R.id.btnRequestReset).isEnabled = false
        findViewById<MaterialButton>(R.id.btnVerifyCode).isEnabled = false
    }

    override fun hideLoading() {
        btnSubmit.isEnabled = true
        findViewById<MaterialButton>(R.id.btnRequestReset).isEnabled = true
        findViewById<MaterialButton>(R.id.btnVerifyCode).isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
