package com.example.guardband.ui.signup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.guardband.R
import com.example.guardband.data.model.EmergencyContact
import com.example.guardband.data.model.ValidationResult
import com.example.guardband.ui.loading.LoadingActivity

class SignUpWizardActivity : AppCompatActivity(),
    SignUpContract.View,
    SignUpNameFragment.Host,
    SignUpLocationFragment.Host,
    SignUpContactsFragment.Host {

    companion object {
        const val EXTRA_START_STEP = "extra_start_step"
        const val EXTRA_FIRST_NAME = "extra_first_name"
        const val EXTRA_LAST_NAME = "extra_last_name"
        const val EXTRA_EMAIL = "extra_email"
        const val STEP_NAME = 1
        const val STEP_LOCATION = 2
        const val STEP_CONTACTS = 3
    }

    private lateinit var tvStep: TextView
    private lateinit var progress1: View
    private lateinit var progress2: View
    private lateinit var progress3: View

    private val presenter = SignUpPresenter()
    private var pendingContacts: List<EmergencyContact>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_wizard)

        tvStep = findViewById(R.id.tvStep)
        progress1 = findViewById(R.id.progress1)
        progress2 = findViewById(R.id.progress2)
        progress3 = findViewById(R.id.progress3)

        val startStep = intent.getIntExtra(EXTRA_START_STEP, STEP_NAME)
        presenter.seedFromGoogle(
            first = intent.getStringExtra(EXTRA_FIRST_NAME).orEmpty(),
            last = intent.getStringExtra(EXTRA_LAST_NAME).orEmpty(),
            mail = intent.getStringExtra(EXTRA_EMAIL).orEmpty(),
            startStep = startStep
        )
        presenter.attachView(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            if (presenter.getCurrentStep() == 1) finish() else presenter.onBack()
        }
        findViewById<TextView>(R.id.tvSkip).setOnClickListener { presenter.onSkip() }

        // Restore / show the correct step without stacking fragments.
        if (savedInstanceState == null) {
            showStep(presenter.getCurrentStep())
        }
        updateStepIndicator(presenter.getCurrentStep())
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    override fun showStep(step: Int) {
        val fragment: Fragment = when (step) {
            2 -> SignUpLocationFragment()
            3 -> SignUpContactsFragment()
            else -> SignUpNameFragment()
        }
        // commitNow avoids racing renderContacts before the view exists (crash → Login).
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, "signup_step_$step")
            .commitNow()

        if (step == 3) {
            pendingContacts?.let { contacts ->
                (supportFragmentManager.findFragmentByTag("signup_step_3") as? SignUpContactsFragment)
                    ?.renderContacts(contacts)
                pendingContacts = null
            }
        }
    }

    override fun updateStepIndicator(step: Int) {
        tvStep.text = "Step $step/3"
        val active = ContextCompat.getColor(this, R.color.brand_primary)
        val inactive = 0xFFE0E0E0.toInt()
        progress1.setBackgroundColor(if (step >= 1) active else inactive)
        progress2.setBackgroundColor(if (step >= 2) active else inactive)
        progress3.setBackgroundColor(if (step >= 3) active else inactive)
    }

    override fun renderContacts(contacts: List<EmergencyContact>) {
        val fragment = supportFragmentManager.findFragmentByTag("signup_step_3") as? SignUpContactsFragment
        if (fragment != null && fragment.view != null) {
            fragment.renderContacts(contacts)
        } else {
            pendingContacts = contacts
        }
    }

    override fun clearContactForm() {
        (supportFragmentManager.findFragmentByTag("signup_step_3") as? SignUpContactsFragment)
            ?.clearForm()
    }

    override fun navigateToLoading() {
        startActivity(
            Intent(this, LoadingActivity::class.java).apply {
                putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_MAIN)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        // Do not finish() before Loading starts — CLEAR_TASK already replaces the stack.
    }

    override fun showFieldError(field: String, message: String?) {
        when (field) {
            "firstName", "lastName", "email", "password" ->
                (supportFragmentManager.findFragmentByTag("signup_step_1") as? SignUpNameFragment)
                    ?.showErrors(field, message)
            "location" ->
                (supportFragmentManager.findFragmentByTag("signup_step_2") as? SignUpLocationFragment)
                    ?.showLocationError(message)
            else -> {
                (supportFragmentManager.findFragmentByTag("signup_step_3") as? SignUpContactsFragment)
                    ?.showFieldError(field, message)
                if (message != null) showError(message)
            }
        }
    }

    override fun updatePasswordCriteria(rules: ValidationResult.PasswordRules, hasTyped: Boolean) {
        (supportFragmentManager.findFragmentByTag("signup_step_1") as? SignUpNameFragment)
            ?.updatePasswordCriteria(rules, hasTyped)
    }

    override fun onNameContinue(firstName: String, lastName: String, email: String, password: String) {
        presenter.onNameContinue(firstName, lastName, email, password)
    }

    override fun onPasswordTyped(password: String) {
        presenter.onPasswordTyped(password)
    }

    override fun onLocationContinue(location: String) {
        presenter.onLocationContinue(location)
    }

    override fun onAddContact(name: String, phone: String, relationship: String) {
        presenter.onAddContact(name, phone, relationship)
    }

    override fun onDeleteContact(contactId: String) {
        presenter.onDeleteContact(contactId)
    }

    override fun onContactsContinue() {
        presenter.onContactsContinue()
    }

    override fun showLoading() {}
    override fun hideLoading() {}
    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
