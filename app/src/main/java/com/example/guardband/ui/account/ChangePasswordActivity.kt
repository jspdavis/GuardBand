package com.example.guardband.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.LoginNavigation
import com.example.guardband.ui.auth.PasswordCriteriaList
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Change password, opened from the row of that name in Account information
 * (it was a Settings row until 2026-10-27). Password accounts only: Account
 * information shows a Google account an empty state instead of the row, and
 * [ChangePasswordViewModel] checks again.
 *
 * Two fields, new and confirm, with the same rules checklist the reset screen
 * shows. Submit asks for the current password in the re-sign-in dialog Delete
 * account uses, then the ViewModel re-authenticates and updates.
 *
 * Reuses the view ids of `activity_reset_password.xml` (the layout was copied
 * from it), which is why they read `reset`.
 *
 * Flow: Account information → ChangePasswordActivity → (done, Back or Cancel) Account information
 *       ChangePasswordActivity → (session gone) → LoginActivity (task cleared)
 */
class ChangePasswordActivity : AppCompatActivity() {

    private val viewModel: ChangePasswordViewModel by viewModels { ChangePasswordViewModel.Factory }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(this) }

    private lateinit var ibBack: ImageButton
    private lateinit var etNew: TextInputEditText
    private lateinit var etConfirm: TextInputEditText
    private lateinit var tvError: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        ibBack = findViewById(R.id.ib_reset_back)
        etNew = findViewById(R.id.et_reset_password)
        etConfirm = findViewById(R.id.et_reset_confirm)
        tvError = findViewById(R.id.tv_reset_message)
        btnSubmit = findViewById(R.id.btn_reset_submit)
        btnCancel = findViewById(R.id.btn_reset_cancel)
        progress = findViewById(R.id.progress_reset)

        ibBack.setOnClickListener { viewModel.onCancelled() }
        btnCancel.setOnClickListener { viewModel.onCancelled() }
        btnSubmit.setOnClickListener {
            viewModel.onSubmitClicked(etNew.text.toString(), etConfirm.text.toString())
        }
        etNew.doAfterTextChanged { notifyFieldsChanged() }
        etConfirm.doAfterTextChanged { notifyFieldsChanged() }

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
    }

    private fun notifyFieldsChanged() {
        viewModel.onFieldsChanged(etNew.text.toString(), etConfirm.text.toString())
    }

    private fun render(state: ChangePasswordUiState) {
        PasswordCriteriaList.render(findViewById(android.R.id.content), state.criteria)

        progress.visibility = if (state.isBusy) View.VISIBLE else View.GONE

        // Everything goes flat while the change runs.
        val enabled = !state.isBusy
        ibBack.isEnabled = enabled
        btnCancel.isEnabled = enabled
        etNew.isEnabled = enabled
        etConfirm.isEnabled = enabled
        btnSubmit.isEnabled = enabled && state.criteria.allMet

        tvError.text = state.errorMessage.orEmpty()
        tvError.visibility = if (state.errorMessage != null) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: ChangePasswordEvent) {
        when (event) {
            is ChangePasswordEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()

            ChangePasswordEvent.AskForPassword -> askForPassword()

            ChangePasswordEvent.Close -> finish()

            ChangePasswordEvent.NavigateToLogin -> lifecycleScope.launch {
                LoginNavigation.clearCredentialsThenGoToLogin(
                    this@ChangePasswordActivity,
                    googleIdTokenProvider
                )
            }
        }
    }

    /**
     * Collects the current password, in the same dialog layout Delete account
     * uses. Only the password leaves it: the repository takes the email from
     * the live session.
     */
    private fun askForPassword() {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_reauth_password, null)
        content.findViewById<TextView>(R.id.tv_reauth_password_message)
            .setText(R.string.dialog_change_password_message)
        val etCurrent = content.findViewById<TextInputEditText>(R.id.et_reauth_password)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_change_password_title)
            .setView(content)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_change_password) { _, _ ->
                viewModel.onPasswordSubmitted(
                    etCurrent.text.toString(),
                    etNew.text.toString(),
                    etConfirm.text.toString()
                )
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }
}