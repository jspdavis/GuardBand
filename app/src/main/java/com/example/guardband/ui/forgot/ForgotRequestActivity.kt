package com.example.guardband.ui.forgot

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Forgot Password — one screen, two faces.
 *
 * The form asks for the email; after a request has gone out the same screen
 * becomes "check your email", with Resend on a countdown. There is no second
 * Activity any more: `ForgotSuccessActivity` was removed when Resend arrived,
 * because resending needs the address and only this screen has it.
 *
 * **The link in that email opens the app, not a web page** — see
 * [ResetPasswordActivity][com.example.guardband.ui.reset.ResetPasswordActivity]
 * and `docs/forgot-password.md`. Nothing on this screen knows that; Firebase
 * decides what the link points at, from the Console's email template.
 *
 * The sent wording is identical whether or not the address is registered, and
 * [ForgotRequestViewModel] is what keeps it that way.
 *
 * View ids: ib_forgot_request_back, tv_forgot_request_title,
 * tv_forgot_request_subtitle, til_forgot_request_email,
 * et_forgot_request_email, btn_forgot_request_send,
 * btn_forgot_request_cancel, progress_forgot_request.
 *
 * Flow: Login → ForgotRequestActivity → (email) → ResetPasswordActivity
 */
class ForgotRequestActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var tvSubtitle: android.widget.TextView
    private lateinit var btnSend: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar

    private val viewModel: ForgotRequestViewModel by viewModels { ForgotRequestViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_request)

        etEmail     = findViewById(R.id.et_forgot_request_email)
        tvSubtitle  = findViewById(R.id.tv_forgot_request_subtitle)
        btnSend     = findViewById(R.id.btn_forgot_request_send)
        btnCancel   = findViewById(R.id.btn_forgot_request_cancel)
        progressBar = findViewById(R.id.progress_forgot_request)

        findViewById<ImageButton>(R.id.ib_forgot_request_back).setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            viewModel.onSendClicked(etEmail.text.toString())
        }

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

    /**
     * One button serves Request and Resend, relabelled from the state.
     *
     * While the cooldown runs it counts down rather than going blank, so the
     * user can see that waiting is the thing to do.
     */
    private fun render(state: ForgotRequestUiState) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        tvSubtitle.setText(
            if (state.sent) R.string.label_forgot_sent_subtitle
            else R.string.label_forgot_request_subtitle
        )

        btnSend.text = when {
            !state.sent -> getString(R.string.btn_request_reset_link)
            state.resendSecondsRemaining > 0 ->
                getString(R.string.btn_resend_link_in, state.resendSecondsRemaining)

            else -> getString(R.string.btn_resend_link)
        }
        btnSend.isEnabled = if (state.sent) state.canResend else !state.isLoading

        // Editable after sending too: a mistyped address is the likeliest
        // reason no email arrived, and the only fix is to change it and resend.
        etEmail.isEnabled = !state.isLoading
    }

    private fun handleEvent(event: ForgotRequestEvent) {
        when (event) {
            is ForgotRequestEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
        }
    }
}
