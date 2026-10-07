package com.example.guardband.ui.track

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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.ui.home.HomeViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

/**
 * Track tab (the default tab): top bar with the user chip, notifications bell
 * and settings gear, a map placeholder, the device status pill, round map
 * controls and the Check-in pill.
 *
 * Gear and bell forward to the shared [HomeViewModel], which asks the host to
 * show Settings or Notifications.
 */
class TrackFragment : Fragment(R.layout.fragment_track) {

    private lateinit var tvUserName: TextView

    private val viewModel: TrackViewModel by viewModels { TrackViewModel.Factory }
    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUserName     = view.findViewById(R.id.tv_track_user_name)

        view.findViewById<ImageButton>(R.id.ib_track_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }
        view.findViewById<ImageButton>(R.id.ib_track_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<Button>(R.id.btn_track_check_in)
            .setOnClickListener { viewModel.onCheckInClicked() }
        view.findViewById<FloatingActionButton>(R.id.fab_track_recenter)
            .setOnClickListener { viewModel.onRecenterClicked() }
        view.findViewById<FloatingActionButton>(R.id.fab_track_layers)
            .setOnClickListener { viewModel.onLayersClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }
    }

    private fun render(state: TrackUiState) {
        tvUserName.text = state.userName
    }

    private fun handleEvent(event: TrackEvent) {
        when (event) {
            is TrackEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()

            is TrackEvent.OpenNavigation -> openNavigation(event.url)
        }
    }

    /**
     * D6: hands the position to whatever maps app the device has, via a plain
     * `ACTION_VIEW` on Google Maps' keyless universal URL.
     *
     * A phone with nothing registered for it throws
     * [ActivityNotFoundException], which is reported back through the
     * ViewModel so the wording stays with every other user-facing message
     * rather than being invented here.
     */
    private fun openNavigation(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onNavigationUnavailable()
        }
    }
}
