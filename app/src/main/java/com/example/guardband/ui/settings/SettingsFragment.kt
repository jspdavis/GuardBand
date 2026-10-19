package com.example.guardband.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.example.guardband.ui.account.AccountActivity
import com.example.guardband.ui.auth.GoogleIdTokenProvider
import com.example.guardband.ui.auth.LoginNavigation
import com.example.guardband.ui.changelog.ChangelogActivity
import kotlinx.coroutines.launch

/**
 * Settings, pushed inside the Home host (the host hides the bottom bar while
 * it shows). The back arrow goes through the Activity's Back handling, which
 * returns to the last tab.
 *
 * Static: [SettingsViewModel] holds no state. Account information opens
 * [AccountActivity], which is where Delete account lives now - one level
 * deeper than this list, so the destructive action is not a stray tap away.
 *
 * View ids: ib_settings_back, tv_settings_title, tv_settings_account,
 * tv_settings_change_password, tv_settings_notifications,
 * tv_settings_privacy_notice, tv_settings_changelog, btn_settings_logout.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val googleIdTokenProvider by lazy { GoogleIdTokenProvider(requireActivity()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.ib_settings_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<TextView>(R.id.tv_settings_account)
            .setOnClickListener { viewModel.onAccountClicked() }

        listOf(
            R.id.tv_settings_change_password,
            R.id.tv_settings_notifications
        ).forEach { id ->
            view.findViewById<TextView>(id)
                .setOnClickListener { viewModel.onPlaceholderRowClicked() }
        }

        view.findViewById<TextView>(R.id.tv_settings_privacy_notice)
            .setOnClickListener { viewModel.onPrivacyNoticeClicked() }

        view.findViewById<TextView>(R.id.tv_settings_changelog)
            .setOnClickListener { viewModel.onChangelogClicked() }

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

            SettingsEvent.NavigateToLogin -> viewLifecycleOwner.lifecycleScope.launch {
                LoginNavigation.clearCredentialsThenGoToLogin(requireContext(), googleIdTokenProvider)
            }

            is SettingsEvent.OpenUrl -> openUrl(event.url)

            SettingsEvent.OpenAccount ->
                startActivity(Intent(requireContext(), AccountActivity::class.java))

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
}
