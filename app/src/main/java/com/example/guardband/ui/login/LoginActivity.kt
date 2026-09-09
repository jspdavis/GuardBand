package com.example.guardband.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.guardband.R
import com.example.guardband.data.model.User
import com.example.guardband.ui.forgotpass.ForgotPasswordActivity
import com.example.guardband.ui.loading.LoadingActivity
import com.example.guardband.ui.signup.SignUpWizardActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity(), LoginContract.View {

    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnContinue: MaterialButton
    private lateinit var btnContinueGmail: MaterialButton
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvCreateAccount: TextView

    private val presenter = LoginPresenter()

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) {
                presenter.onGoogleSignInFailed(
                    "Google Sign-In is not configured. Add a Web client OAuth ID in Firebase Console."
                )
            } else {
                presenter.onGoogleIdTokenReceived(token)
            }
        } catch (e: ApiException) {
            presenter.onGoogleSignInFailed(e.localizedMessage ?: "Google Sign-In cancelled.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tilPhone = findViewById(R.id.tilPhone)
        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etPassword)
        btnContinue = findViewById(R.id.btnContinue)
        btnContinueGmail = findViewById(R.id.btnContinueGmail)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvCreateAccount = findViewById(R.id.tvCreateAccount)

        presenter.attachView(this)

        btnContinue.setOnClickListener {
            // Clear any previous inline error when user tries again.
            showIdentifierError(null)
            presenter.onLoginClicked(
                etPhone.text?.toString().orEmpty(),
                etPassword.text?.toString().orEmpty()
            )
        }
        btnContinueGmail.setOnClickListener { presenter.onGoogleSignInClicked() }
        tvForgotPassword.setOnClickListener { presenter.onForgotPasswordClicked() }
        tvCreateAccount.setOnClickListener { presenter.onCreateAccountClicked() }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }

    // ── LoginContract.View ────────────────────────────────────────────────────

    override fun showIdentifierError(message: String?) {
        tilPhone.error = message
    }

    override fun launchGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        if (webClientId.contains("REPLACE") || webClientId.isBlank()) {
            showError("Add your Firebase Web client ID to strings.xml (default_web_client_id).")
            return
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleLauncher.launch(client.signInIntent)
    }

    override fun navigateToMain() {
        startActivity(
            Intent(this, LoadingActivity::class.java).apply {
                putExtra(LoadingActivity.EXTRA_DESTINATION, LoadingActivity.DEST_MAIN)
            }
        )
        finish()
    }

    override fun navigateToSignUp() {
        startActivity(Intent(this, SignUpWizardActivity::class.java))
    }

    override fun navigateToSignUpLocation(user: User) {
        startActivity(
            Intent(this, SignUpWizardActivity::class.java).apply {
                putExtra(SignUpWizardActivity.EXTRA_START_STEP, SignUpWizardActivity.STEP_LOCATION)
                putExtra(SignUpWizardActivity.EXTRA_FIRST_NAME, user.firstName)
                putExtra(SignUpWizardActivity.EXTRA_LAST_NAME, user.lastName)
                putExtra(SignUpWizardActivity.EXTRA_EMAIL, user.email)
            }
        )
    }

    override fun navigateToForgotPassword() {
        startActivity(Intent(this, ForgotPasswordActivity::class.java))
    }

    override fun showLoading() {
        btnContinue.isEnabled = false
        btnContinueGmail.isEnabled = false
    }

    override fun hideLoading() {
        btnContinue.isEnabled = true
        btnContinueGmail.isEnabled = true
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
