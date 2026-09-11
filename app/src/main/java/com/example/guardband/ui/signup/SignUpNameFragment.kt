package com.example.guardband.ui.signup

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.guardband.R
import com.example.guardband.data.model.ValidationResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SignUpNameFragment : Fragment() {

    interface Host {
        fun onNameContinue(firstName: String, lastName: String, email: String, password: String)
        fun onPasswordTyped(password: String)
    }

    private var host: Host? = null

    // Rule tag views — populated in onViewCreated
    private var tvRule1: TextView? = null
    private var tvRule2: TextView? = null
    private var tvRule3: TextView? = null
    private var tvRule4: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_signup_name, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        host = activity as? Host

        // Host activity already owns Back / Step / Skip — hide fragment duplicates.
        view.findViewById<View?>(R.id.btnBack)?.visibility = View.GONE
        view.findViewById<View?>(R.id.btnSkip)?.visibility = View.GONE
        view.findViewById<View?>(R.id.layoutStepIndicator)?.visibility = View.GONE

        tvRule1 = view.findViewById(R.id.tvSignUpRule1)
        tvRule2 = view.findViewById(R.id.tvSignUpRule2)
        tvRule3 = view.findViewById(R.id.tvSignUpRule3)
        tvRule4 = view.findViewById(R.id.tvSignUpRule4)

        val etPassword = view.findViewById<TextInputEditText>(R.id.etPassword)

        // Live password criteria feedback — relay every keystroke to the presenter.
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                host?.onPasswordTyped(s?.toString().orEmpty())
            }
        })

        view.findViewById<MaterialButton>(R.id.btnContinue).setOnClickListener {
            host?.onNameContinue(
                view.findViewById<TextInputEditText>(R.id.etFirstName).text?.toString().orEmpty(),
                view.findViewById<TextInputEditText>(R.id.etLastName).text?.toString().orEmpty(),
                view.findViewById<TextInputEditText>(R.id.etEmail).text?.toString().orEmpty(),
                etPassword.text?.toString().orEmpty()
            )
        }
    }

    // ── Public API called by SignUpWizardActivity ─────────────────────────────

    fun showErrors(field: String, message: String?) {
        val v = view ?: return
        when (field) {
            "firstName" -> v.findViewById<TextInputLayout>(R.id.tilFirstName).error = message
            "lastName"  -> v.findViewById<TextInputLayout>(R.id.tilLastName).error = message
            "email"     -> v.findViewById<TextInputLayout>(R.id.tilEmail).error = message
            "password"  -> v.findViewById<TextInputLayout>(R.id.tilPassword).error = message
        }
    }

    /**
     * Updates the 4 live password criteria tags based on the current [rules].
     * [hasTyped] is false until the user starts typing, keeping tags neutral.
     */
    fun updatePasswordCriteria(rules: ValidationResult.PasswordRules, hasTyped: Boolean) {
        bindRule(tvRule1, "At least 8 characters", rules.minLength, hasTyped)
        bindRule(tvRule2, "At least 1 uppercase letter", rules.hasUppercase, hasTyped)
        bindRule(tvRule3, "At least 1 lowercase letter", rules.hasLowercase, hasTyped)
        bindRule(tvRule4, "At least 1 number or special character", rules.hasNumberOrSpecial, hasTyped)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun bindRule(tv: TextView?, label: String, met: Boolean, hasTyped: Boolean) {
        tv ?: return
        val ctx = context ?: return
        val icon = when {
            !hasTyped -> "☐"
            met       -> "☑"
            else      -> "☒"
        }
        tv.text = "$icon $label"
        tv.setTextColor(
            ContextCompat.getColor(
                ctx,
                when {
                    !hasTyped -> R.color.rule_neutral
                    met       -> R.color.rule_met
                    else      -> R.color.rule_unmet
                }
            )
        )
    }
}
