package com.example.guardband.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.GoogleIdTokenResult
import com.example.guardband.ui.auth.LoginNavigation
import com.example.guardband.ui.login.LoginActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Account information, opened from the row of that name in Settings.
 *
 * The account's details on top, and **Delete account in a danger zone at the
 * very bottom**. It used to be a row at the foot of Settings itself; one level
 * deeper, behind a screen that is about the account, it is much harder to hit
 * by accident. Every safeguard came with it unchanged: the modal that spells
 * out what goes, the re-authentication, the retry guards and the Back block.
 *
 * Two ViewModels, the same split Settings had: [AccountViewModel] shows the
 * account, [DeleteAccountViewModel] owns the one flow here with real state.
 *
 * An Activity rather than a Fragment pushed over the Home host, for the same
 * reason as the Changelog: the host's Back always returns to a tab, and from
 * here Back has to return to Settings. Being outside the host also puts it
 * outside the host's auth gate, so [AccountViewModel] has one of its own.
 *
 * **Email verification** sits in the details card: "Verified" or "Not
 * verified", and for a password account that is not verified yet, Resend on a
 * cooldown. The session is reloaded every time the screen comes to the front,
 * so a link opened in the inbox shows up on return. (An unverified password
 * account is stopped before the tabs by the Home gate, and reaches this
 * screen only from the gate's "Wrong email? Delete this account".)
 *
 * View ids: ib_account_back, tv_account_title, card_account_details,
 * tv_account_name, tv_account_email, tv_account_verification,
 * btn_account_resend_verification, tv_account_verification_hint,
 * tv_account_danger_title,
 * card_account_danger, tv_account_danger_explainer, tv_account_delete,
 * card_account_delete_failure, tv_account_delete_error,
 * btn_account_delete_log_out, btn_account_delete_retry,
 * progress_account_delete.
 *
 * **It is also where an unfinished deletion is finished.** When an account's
 * data was deleted but its sign-in survived, the Home gate opens this screen
 * with the task cleared, and [DeleteAccountViewModel] starts in "finish
 * deleting": the failure card says what happened, Retry asks who they are and
 * removes the sign-in, and Log Out leaves it waiting for the next sign-in.
 *
 * Flow: Settings → AccountActivity → (Back) Settings
 *       AccountActivity → (Delete account, done) → LoginActivity (task cleared)
 *       Home gate, deletion pending → AccountActivity (task cleared)
 */
class AccountActivity : AppCompatActivity() {

    private val viewModel: AccountViewModel by viewModels { AccountViewModel.Factory }
    private val deleteViewModel: DeleteAccountViewModel by viewModels {
        DeleteAccountViewModel.Factory
    }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(this) }

    private lateinit var ibBack: ImageButton
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvVerification: TextView
    private lateinit var tvVerificationHint: TextView
    private lateinit var btnResend: Button
    private lateinit var tvDeleteAccount: TextView
    private lateinit var cardDeleteFailure: MaterialCardView
    private lateinit var tvDeleteError: TextView
    private lateinit var btnDeleteRetry: Button
    private lateinit var btnDeleteLogOut: Button
    private lateinit var progressDelete: ProgressBar

    /**
     * Swallows Back while a delete is in flight. Leaving mid-delete would hide
     * a half-finished delete behind Settings, which is the one thing the retry
     * guards exist to prevent.
     */
    private val blockBackWhileDeleting = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        ibBack = findViewById(R.id.ib_account_back)
        ibBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tvName = findViewById(R.id.tv_account_name)
        tvEmail = findViewById(R.id.tv_account_email)
        tvVerification = findViewById(R.id.tv_account_verification)
        tvVerificationHint = findViewById(R.id.tv_account_verification_hint)
        btnResend = findViewById(R.id.btn_account_resend_verification)
        btnResend.setOnClickListener { viewModel.onResendClicked() }

        tvDeleteAccount = findViewById(R.id.tv_account_delete)
        tvDeleteAccount.setOnClickListener { deleteViewModel.onDeleteAccountClicked() }

        cardDeleteFailure = findViewById(R.id.card_account_delete_failure)
        tvDeleteError = findViewById(R.id.tv_account_delete_error)
        btnDeleteRetry = findViewById(R.id.btn_account_delete_retry)
        btnDeleteRetry.setOnClickListener { deleteViewModel.onRetryClicked() }
        btnDeleteLogOut = findViewById(R.id.btn_account_delete_log_out)
        btnDeleteLogOut.setOnClickListener { deleteViewModel.onLogOutClicked() }
        progressDelete = findViewById(R.id.progress_account_delete)

        onBackPressedDispatcher.addCallback(this, blockBackWhileDeleting)

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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                deleteViewModel.uiState.collect(::renderDelete)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                deleteViewModel.events.collect(::handleDeleteEvent)
            }
        }
    }

    /** Every return to the screen, including back from the email app. */
    override fun onStart() {
        super.onStart()
        viewModel.onScreenShown()
    }

    private fun render(state: AccountUiState) {
        tvName.text = state.name
        tvEmail.text = state.email

        tvVerification.setText(
            if (state.emailVerified) R.string.label_account_verified
            else R.string.label_account_not_verified
        )
        tvVerification.setTextColor(
            ContextCompat.getColor(
                this,
                if (state.emailVerified) R.color.success_green else R.color.text_primary
            )
        )
        tvVerificationHint.visibility = if (state.showResend) View.VISIBLE else View.GONE

        btnResend.visibility = if (state.showResend) View.VISIBLE else View.GONE
        btnResend.isEnabled = state.resendEnabled
        btnResend.text = if (state.resendSecondsRemaining > 0) {
            getString(R.string.btn_account_resend_in, state.resendSecondsRemaining)
        } else {
            getString(R.string.btn_account_resend)
        }
    }

    private fun renderDelete(state: DeleteAccountUiState) {
        progressDelete.visibility = if (state.isBusy) View.VISIBLE else View.GONE

        // Everything goes flat while a delete runs, so nothing else on the
        // screen can start a second thing on top of it.
        val enabled = !state.isBusy
        ibBack.isEnabled = enabled
        tvDeleteAccount.isEnabled = enabled
        btnDeleteRetry.isEnabled = enabled
        btnDeleteLogOut.isEnabled = enabled
        btnDeleteLogOut.visibility = if (state.isUnfinished) View.VISIBLE else View.GONE

        val failure = state.errorMessage
        if (state.step == DeleteAccountStep.FAILED && failure != null) {
            tvDeleteError.text = failure
            cardDeleteFailure.visibility = View.VISIBLE
        } else {
            cardDeleteFailure.visibility = View.GONE
        }

        blockBackWhileDeleting.isEnabled = state.isBusy
    }

    private fun handleEvent(event: AccountEvent) {
        when (event) {
            is AccountEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()

            AccountEvent.NavigateToLogin -> {
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
        }
    }

    private fun handleDeleteEvent(event: DeleteAccountEvent) {
        when (event) {
            is DeleteAccountEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()

            DeleteAccountEvent.ShowConfirmation -> showDeleteConfirmation()
            DeleteAccountEvent.AskForPassword -> askForPassword()
            DeleteAccountEvent.RequestGoogleIdToken -> requestGoogleIdToken()
            DeleteAccountEvent.NavigateToLogin -> lifecycleScope.launch {
                LoginNavigation.clearCredentialsThenGoToLogin(
                    this@AccountActivity,
                    googleIdTokenProvider
                )
            }
        }
    }

    // ── Delete account ────────────────────────────────────────────────────────

    /**
     * Spells out what is about to go, then asks.
     *
     * Delete sits in the positive slot where the confirming action belongs, but
     * focus is moved to Cancel afterwards so a stray Enter or an accessibility
     * action cannot fire the irreversible one. Dismissing the dialog any way at
     * all routes to `onCancelled`, which resets the flow — safe here, because
     * nothing has been written yet.
     */
    private fun showDeleteConfirmation() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_account_title)
            .setMessage(R.string.dialog_delete_account_message)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> deleteViewModel.onCancelled() }
            .setPositiveButton(R.string.btn_delete_account) { _, _ ->
                deleteViewModel.onConfirmed()
            }
            .setOnCancelListener { deleteViewModel.onCancelled() }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }

    /**
     * Collects the current password for a password account.
     *
     * Only the password leaves this dialog. The email is read from the live
     * session inside the repository, so nothing here can aim the
     * re-authentication at another record.
     */
    private fun askForPassword() {
        val content = LayoutInflater.from(this)
            .inflate(R.layout.dialog_reauth_password, null)
        val etPassword = content.findViewById<TextInputEditText>(R.id.et_reauth_password)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_reauth_password_title)
            .setView(content)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> deleteViewModel.onCancelled() }
            .setPositiveButton(R.string.btn_delete_account) { _, _ ->
                deleteViewModel.onPasswordSubmitted(etPassword.text.toString())
            }
            .setOnCancelListener { deleteViewModel.onCancelled() }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }

    /**
     * Re-authenticates a Google account through the chooser.
     *
     * The same split sign-in uses: Credential Manager needs an Activity, so the
     * request runs here and the ViewModel receives the token string or a
     * [GoogleIdTokenResult].
     */
    private fun requestGoogleIdToken() {
        lifecycleScope.launch {
            when (val result = googleIdTokenProvider.requestIdToken()) {
                is GoogleIdTokenResult.Success -> deleteViewModel.onGoogleIdToken(result.idToken)
                else -> deleteViewModel.onGoogleError(result)
            }
        }
    }
}
