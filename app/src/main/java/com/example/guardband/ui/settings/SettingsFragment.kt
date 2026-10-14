package com.example.guardband.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.GoogleIdTokenResult
import com.example.guardband.ui.changelog.ChangelogActivity
import com.example.guardband.ui.login.LoginActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Settings, pushed inside the Home host (the host hides the bottom bar while
 * it shows). The back arrow goes through the Activity's Back handling, which
 * returns to the last tab.
 *
 * Two ViewModels, deliberately. [SettingsViewModel] is the static screen —
 * four placeholder rows and Log Out, no state at all. [DeleteAccountViewModel]
 * owns the one flow here that has state worth the name: a confirmation, a
 * re-authentication, two writes and a retry that resumes.
 *
 * Both reach Login the same way, through [clearCredentialsThenGoToLogin]: the
 * Credential Manager clear needs a Context, which is why it is here and not in
 * either ViewModel.
 *
 * View ids: ib_settings_back, tv_settings_title, tv_settings_account,
 * tv_settings_change_password, tv_settings_notifications, tv_settings_privacy,
 * tv_settings_privacy_notice, tv_settings_changelog, btn_settings_logout,
 * card_settings_delete_failure, tv_settings_delete_error,
 * btn_settings_delete_retry, tv_settings_delete_account,
 * progress_settings_delete.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val deleteViewModel: DeleteAccountViewModel by viewModels {
        DeleteAccountViewModel.Factory
    }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(requireActivity()) }

    private lateinit var placeholderRows: List<TextView>
    private lateinit var btnLogout: Button
    private lateinit var tvPrivacyNotice: TextView
    private lateinit var tvChangelog: TextView
    private lateinit var tvDeleteAccount: TextView
    private lateinit var cardDeleteFailure: MaterialCardView
    private lateinit var tvDeleteError: TextView
    private lateinit var btnDeleteRetry: Button
    private lateinit var progressDelete: ProgressBar

    /**
     * Swallows Back while a delete is in flight.
     *
     * Added from the Fragment, so it sits in front of `HomeActivity`'s own
     * "back to the last tab" callback — the dispatcher runs the most recently
     * added enabled callback first. Leaving the screen mid-delete would hide a
     * half-finished delete behind a tab, which is the one thing the retry
     * guards exist to prevent.
     */
    private val blockBackWhileDeleting = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_settings_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        placeholderRows = listOf(
            R.id.tv_settings_account,
            R.id.tv_settings_change_password,
            R.id.tv_settings_notifications,
            R.id.tv_settings_privacy
        ).map { id ->
            view.findViewById<TextView>(id).apply {
                setOnClickListener { viewModel.onPlaceholderRowClicked() }
            }
        }

        tvPrivacyNotice = view.findViewById(R.id.tv_settings_privacy_notice)
        tvPrivacyNotice.setOnClickListener { viewModel.onPrivacyNoticeClicked() }

        tvChangelog = view.findViewById(R.id.tv_settings_changelog)
        tvChangelog.setOnClickListener { viewModel.onChangelogClicked() }

        btnLogout = view.findViewById(R.id.btn_settings_logout)
        btnLogout.setOnClickListener { viewModel.onLogoutClicked() }

        tvDeleteAccount = view.findViewById(R.id.tv_settings_delete_account)
        tvDeleteAccount.setOnClickListener { deleteViewModel.onDeleteAccountClicked() }

        cardDeleteFailure = view.findViewById(R.id.card_settings_delete_failure)
        tvDeleteError = view.findViewById(R.id.tv_settings_delete_error)
        btnDeleteRetry = view.findViewById(R.id.btn_settings_delete_retry)
        btnDeleteRetry.setOnClickListener { deleteViewModel.onRetryClicked() }
        progressDelete = view.findViewById(R.id.progress_settings_delete)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            blockBackWhileDeleting
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                deleteViewModel.uiState.collect(::render)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                deleteViewModel.events.collect(::handleDeleteEvent)
            }
        }
    }

    private fun render(state: DeleteAccountUiState) {
        progressDelete.visibility = if (state.isBusy) View.VISIBLE else View.GONE

        // Everything goes flat while a delete runs, so nothing else on the
        // screen can start a second thing on top of it.
        val enabled = !state.isBusy
        placeholderRows.forEach { it.isEnabled = enabled }
        btnLogout.isEnabled = enabled
        tvPrivacyNotice.isEnabled = enabled
        tvChangelog.isEnabled = enabled
        tvDeleteAccount.isEnabled = enabled
        btnDeleteRetry.isEnabled = enabled

        val failure = state.errorMessage
        if (state.step == DeleteAccountStep.FAILED && failure != null) {
            tvDeleteError.text = failure
            cardDeleteFailure.visibility = View.VISIBLE
        } else {
            cardDeleteFailure.visibility = View.GONE
        }

        blockBackWhileDeleting.isEnabled = state.isBusy
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()

            SettingsEvent.NavigateToLogin -> clearCredentialsThenGoToLogin()

            is SettingsEvent.OpenUrl -> openUrl(event.url)

            SettingsEvent.OpenChangelog ->
                startActivity(Intent(requireContext(), ChangelogActivity::class.java))
        }
    }

    /**
     * Opens a hosted page in whatever browser the phone has.
     *
     * A plain `ACTION_VIEW`, so it needs no permission and no extra
     * dependency. A phone with nothing registered for `https` throws
     * [ActivityNotFoundException], which goes back through the ViewModel so
     * the wording stays with every other user-facing message.
     */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onBrowserUnavailable()
        }
    }

    private fun handleDeleteEvent(event: DeleteAccountEvent) {
        when (event) {
            is DeleteAccountEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_LONG).show()

            DeleteAccountEvent.ShowConfirmation -> showDeleteConfirmation()
            DeleteAccountEvent.AskForPassword -> askForPassword()
            DeleteAccountEvent.RequestGoogleIdToken -> requestGoogleIdToken()
            DeleteAccountEvent.NavigateToLogin -> clearCredentialsThenGoToLogin()
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
        val dialog = MaterialAlertDialogBuilder(requireContext())
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
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_reauth_password, null)
        val etPassword = content.findViewById<TextInputEditText>(R.id.et_reauth_password)

        val dialog = MaterialAlertDialogBuilder(requireContext())
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
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = googleIdTokenProvider.requestIdToken()) {
                is GoogleIdTokenResult.Success -> deleteViewModel.onGoogleIdToken(result.idToken)
                else -> deleteViewModel.onGoogleError(result)
            }
        }
    }

    // ── Leaving ───────────────────────────────────────────────────────────────

    /**
     * Forgets the stored Google account, then goes to Login with the back
     * stack cleared.
     *
     * Shared by Log Out and by a finished delete. The clear is bounded by
     * [CLEAR_CREDENTIALS_TIMEOUT_MS] and its failures are swallowed inside the
     * provider: the user has already been signed out of Firebase by this point
     * — and after a delete the account does not exist at all — so nothing here
     * may keep them on this screen. Worst case the chooser remembers the
     * account, which is a nuisance, not a lock-in.
     */
    private fun clearCredentialsThenGoToLogin() {
        viewLifecycleOwner.lifecycleScope.launch {
            withTimeoutOrNull(CLEAR_CREDENTIALS_TIMEOUT_MS) {
                googleIdTokenProvider.clearCredentialState()
            }
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    private companion object {
        const val CLEAR_CREDENTIALS_TIMEOUT_MS = 2_000L
    }
}
