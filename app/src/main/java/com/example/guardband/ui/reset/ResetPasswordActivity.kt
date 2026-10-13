package com.example.guardband.ui.reset

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.BuildConfig
import com.example.guardband.R
import com.example.guardband.ui.auth.PasswordCriteriaList
import com.example.guardband.ui.forgot.ForgotRequestActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * New Credentials — opened by the App Link in Firebase's password-reset email.
 *
 * The one Activity in the app that is `exported="true"`, because the whole
 * point is for an external click to reach it. Everything it trusts from that
 * Intent goes through [ResetLinkParser] first, which re-checks the host, path
 * and mode rather than assuming the manifest filter was only matched
 * honestly.
 *
 * The host and path come from `BuildConfig.RESET_LINK_HOST` /
 * `RESET_LINK_PATH`, which the manifest's intent filter also reads as
 * manifest placeholders. Both are declared once in `app/build.gradle.kts` —
 * see `docs/forgot-password.md`.
 *
 * **Nothing here logs**, and the `oobCode` never leaves the ViewModel: not
 * into an extra, not into `onSaveInstanceState`, not into a message. The code
 * is therefore lost on process death and the user has to re-open the email
 * link, which is the deliberate cost of not writing a password-change
 * credential to disk.
 *
 * **A signed-in user is left alone.** The session is neither read nor
 * cleared; the reset is a project-level operation keyed by the code. Success
 * still ends at Login, because the password they had has just stopped being
 * the password.
 *
 * View ids: ib_reset_back, tv_reset_title, tv_reset_email,
 * group_reset_form, include_reset_criteria, til_reset_password,
 * et_reset_password, til_reset_confirm, et_reset_confirm, btn_reset_submit,
 * btn_reset_cancel, group_reset_dead, tv_reset_message,
 * btn_reset_request_new, btn_reset_retry_verify, progress_reset.
 *
 * Flow: (email link) → ResetPasswordActivity → ResetSuccessActivity → LoginActivity
 * (back stack cleared at each step)
 */
class ResetPasswordActivity : AppCompatActivity() {

    private val viewModel: ResetPasswordViewModel by viewModels {
        ResetPasswordViewModel.Factory
    }

    private lateinit var tvEmail: TextView
    private lateinit var tvMessage: TextView
    private lateinit var groupForm: Group
    private lateinit var groupDead: Group
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirm: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    private lateinit var btnRequestNew: Button
    private lateinit var btnRetryVerify: Button
    private lateinit var progress: ProgressBar

    /** Swallows Back while a call is in flight, so a half-done reset cannot be walked out of. */
    private val blockBackWhileBusy = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        tvEmail = findViewById(R.id.tv_reset_email)
        tvMessage = findViewById(R.id.tv_reset_message)
        groupForm = findViewById(R.id.group_reset_form)
        groupDead = findViewById(R.id.group_reset_dead)
        tilPassword = findViewById(R.id.til_reset_password)
        etPassword = findViewById(R.id.et_reset_password)
        etConfirm = findViewById(R.id.et_reset_confirm)
        btnSubmit = findViewById(R.id.btn_reset_submit)
        btnCancel = findViewById(R.id.btn_reset_cancel)
        btnRequestNew = findViewById(R.id.btn_reset_request_new)
        btnRetryVerify = findViewById(R.id.btn_reset_retry_verify)
        progress = findViewById(R.id.progress_reset)

        findViewById<ImageButton>(R.id.ib_reset_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        btnCancel.setOnClickListener { finish() }
        btnSubmit.setOnClickListener {
            viewModel.onSubmitClicked(etPassword.text.toString(), etConfirm.text.toString())
        }
        btnRequestNew.setOnClickListener { viewModel.onRequestNewLinkClicked() }
        btnRetryVerify.setOnClickListener { viewModel.onRetryVerifyClicked() }

        val criteriaWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.onPasswordChanged(
                    etPassword.text.toString(),
                    etConfirm.text.toString()
                )
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        }
        etPassword.addTextChangedListener(criteriaWatcher)
        etConfirm.addTextChangedListener(criteriaWatcher)

        onBackPressedDispatcher.addCallback(this, blockBackWhileBusy)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }

        // Only on a genuine cold start. A configuration change re-runs onCreate
        // with the same ViewModel, which already holds the code and the stage,
        // and re-verifying would spend a second call on a code that is fine.
        if (savedInstanceState == null) handleLink(intent)
    }

    /** The link arriving while this Activity is already on top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /**
     * Parses the incoming URL and hands the ViewModel the code, or null.
     *
     * A null is not distinguished from an expired code for the user: both end
     * at "request a new link", and saying which check failed would be a
     * remark about the contents of the link.
     */
    private fun handleLink(intent: Intent?) {
        val url = intent?.data?.toString()
        val code = url?.let {
            ResetLinkParser.oobCodeFrom(
                url = it,
                expectedHost = BuildConfig.RESET_LINK_HOST,
                expectedPath = BuildConfig.RESET_LINK_PATH
            )
        }
        viewModel.onLinkOpened(code)
    }

    private fun render(state: ResetPasswordUiState) {
        progress.visibility = if (state.isBusy) View.VISIBLE else View.GONE

        val showForm = state.stage == ResetPasswordStage.READY ||
            state.stage == ResetPasswordStage.SUBMITTING
        groupForm.visibility = if (showForm) View.VISIBLE else View.GONE
        groupDead.visibility =
            if (state.stage == ResetPasswordStage.LINK_DEAD) View.VISIBLE else View.GONE

        tvEmail.visibility = if (state.email.isEmpty()) View.GONE else View.VISIBLE
        if (state.email.isNotEmpty()) {
            tvEmail.text = getString(R.string.label_reset_for_email, state.email)
        }

        PasswordCriteriaList.render(findViewById(android.R.id.content), state.criteria)

        tilPassword.error = state.fieldError
        tilPassword.isErrorEnabled = state.fieldError != null

        btnSubmit.isEnabled = state.canSubmit
        btnCancel.isEnabled = !state.isBusy
        etPassword.isEnabled = !state.isBusy
        etConfirm.isEnabled = !state.isBusy

        btnRetryVerify.visibility = if (state.canRetryVerify) View.VISIBLE else View.GONE

        // On LINK_DEAD the message is the panel's own text; on READY it is a
        // transient failure the user can submit through, so it goes in a Toast
        // from the event stream instead of here.
        if (state.stage == ResetPasswordStage.LINK_DEAD) {
            tvMessage.text = state.message.orEmpty()
        }

        blockBackWhileBusy.isEnabled = state.isBusy
    }

    private fun handleEvent(event: ResetPasswordEvent) {
        when (event) {
            is ResetPasswordEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()

            ResetPasswordEvent.NavigateToSuccess -> {
                startActivity(
                    Intent(this, ResetSuccessActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }

            ResetPasswordEvent.NavigateToRequest -> {
                startActivity(
                    Intent(this, ForgotRequestActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
        }
    }
}
