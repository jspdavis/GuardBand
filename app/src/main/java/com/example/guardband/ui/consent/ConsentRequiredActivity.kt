package com.example.guardband.ui.consent

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.LoginNavigation
import com.example.guardband.ui.home.HomeActivity
import com.example.guardband.ui.login.LoginActivity
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch

/**
 * "Before you continue" - the consent step for an account that has none,
 * opened by the Home gate with the task cleared.
 *
 * **Uses sign-up's own layout, `activity_signup_consent.xml`,** so the two
 * info rows, the statement on the checkbox and the "Read the Privacy Notice"
 * button are the same views with the same strings: there is one statement,
 * and changing it means bumping `CONSENT_VERSION` for both routes at once.
 * The statement stays the checkbox's own label and the link stays a separate
 * button, so TalkBack reads them as on sign-up (`docs/privacy-and-consent.md`
 * §7). Only the title and the two bottom buttons are relabelled here: Back
 * becomes **Decline** and Get started becomes **Agree**.
 *
 * **Nothing here reaches the tabs without a stored record.** Agree leaves only
 * after the write succeeds, and lands on [HomeActivity], whose gate reads the
 * record again. The system Back leaves the app, because the task holds
 * nothing else; the next launch comes back through the gate to this screen.
 * Recents and process death reopen this screen, not the tabs.
 *
 * View ids (sign-up's): tv_signup_consent_title, cb_signup_consent,
 * btn_signup_consent_privacy, btn_signup_back (Decline),
 * btn_signup_consent_create (Agree), btn_signup_consent_retry,
 * progress_signup_consent.
 *
 * Flow: Home gate → ConsentRequiredActivity → (Agree) HomeActivity
 *       ConsentRequiredActivity → (Decline) LoginActivity
 */
class ConsentRequiredActivity : AppCompatActivity() {

    private companion object {
        const val DISABLED_ALPHA = 0.5f
    }

    private val viewModel: ConsentRequiredViewModel by viewModels {
        ConsentRequiredViewModel.Factory
    }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(this) }

    private lateinit var checkBox: MaterialCheckBox
    private lateinit var btnDecline: Button
    private lateinit var btnAgree: Button
    private lateinit var btnRetry: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_consent)

        checkBox    = findViewById(R.id.cb_signup_consent)
        btnDecline  = findViewById(R.id.btn_signup_back)
        btnAgree    = findViewById(R.id.btn_signup_consent_create)
        btnRetry    = findViewById(R.id.btn_signup_consent_retry)
        progressBar = findViewById(R.id.progress_signup_consent)

        findViewById<TextView>(R.id.tv_signup_consent_title)
            .setText(R.string.label_consent_required_title)
        btnDecline.setText(R.string.btn_consent_required_decline)
        btnAgree.setText(R.string.btn_consent_required_agree)

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onConsentChanged(isChecked)
        }
        btnDecline.setOnClickListener { viewModel.onDeclineClicked() }
        btnAgree.setOnClickListener { viewModel.onAgreeClicked() }
        // The same call: Retry re-runs the write and nothing else.
        btnRetry.setOnClickListener { viewModel.onAgreeClicked() }
        findViewById<View>(R.id.btn_signup_consent_privacy).setOnClickListener {
            viewModel.onPrivacyNoticeClicked()
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

    private fun render(state: ConsentRequiredUiState) {
        progressBar.visibility = if (state.isSaving) View.VISIBLE else View.GONE

        btnAgree.isEnabled = state.consentGiven && !state.isSaving
        btnRetry.isEnabled = !state.isSaving
        btnDecline.isEnabled = !state.isSaving
        // The gradient backgrounds have no disabled state of their own.
        btnAgree.alpha = if (btnAgree.isEnabled) 1f else DISABLED_ALPHA
        btnRetry.alpha = if (btnRetry.isEnabled) 1f else DISABLED_ALPHA
        btnDecline.alpha = if (btnDecline.isEnabled) 1f else DISABLED_ALPHA

        btnRetry.visibility = if (state.saveFailed) View.VISIBLE else View.GONE
        btnAgree.visibility = if (state.saveFailed) View.GONE else View.VISIBLE
    }

    /** A plain `ACTION_VIEW`, the same as sign-up's link. */
    private fun openPrivacyNotice(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onPrivacyNoticeUnavailable()
        }
    }

    private fun handleEvent(event: ConsentRequiredEvent) {
        when (event) {
            is ConsentRequiredEvent.ShowMessage ->
                Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()

            is ConsentRequiredEvent.OpenPrivacyNotice -> openPrivacyNotice(event.url)

            ConsentRequiredEvent.ContinueToHome -> replaceTaskWith(HomeActivity::class.java)

            ConsentRequiredEvent.NavigateToLogin -> replaceTaskWith(LoginActivity::class.java)

            // The same way out as Log Out, so the Google chooser is offered
            // again rather than silently signing the same account back in.
            is ConsentRequiredEvent.Declined -> {
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
                lifecycleScope.launch {
                    LoginNavigation.clearCredentialsThenGoToLogin(
                        this@ConsentRequiredActivity,
                        googleIdTokenProvider
                    )
                }
            }
        }
    }

    private fun replaceTaskWith(target: Class<out AppCompatActivity>) {
        startActivity(
            Intent(this, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
