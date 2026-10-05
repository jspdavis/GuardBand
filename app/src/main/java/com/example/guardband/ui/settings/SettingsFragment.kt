package com.example.guardband.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.login.LoginActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Settings, pushed inside the Home host (the bottom bar stays visible with no
 * tab selected). The back arrow goes through the Activity's Back handling,
 * which returns to the last tab.
 *
 * Log Out also clears the Credential Manager state, so the Google account
 * chooser appears again on the next sign-in instead of silently reusing the
 * last account. That call needs a Context, so it lives here rather than in the
 * ViewModel.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(requireActivity()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_settings_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        listOf(
            R.id.tv_settings_account,
            R.id.tv_settings_change_password,
            R.id.tv_settings_notifications,
            R.id.tv_settings_privacy
        ).forEach { id ->
            view.findViewById<TextView>(id).setOnClickListener { viewModel.onPlaceholderRowClicked() }
        }
        view.findViewById<Button>(R.id.btn_settings_logout)
            .setOnClickListener { viewModel.onLogoutClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()

            SettingsEvent.NavigateToLogin -> clearCredentialsThenGoToLogin()
        }
    }

    /**
     * Forgets the stored Google account, then goes to Login with the back
     * stack cleared.
     *
     * The clear is bounded by [CLEAR_CREDENTIALS_TIMEOUT_MS] and its failures
     * are swallowed inside the provider: the user has already been signed out
     * of Firebase by this point, so nothing here may keep them on this screen.
     * Worst case the chooser remembers the account, which is a nuisance, not a
     * lock-in.
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
