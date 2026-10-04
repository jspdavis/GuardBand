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
import com.example.guardband.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Settings, pushed inside the Home host (the bottom bar stays visible with no
 * tab selected). The back arrow goes through the Activity's Back handling,
 * which returns to the last tab.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

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

            SettingsEvent.NavigateToLogin ->
                startActivity(
                    Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
        }
    }
}
