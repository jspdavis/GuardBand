package com.example.guardband.ui.track

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
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
import com.example.guardband.data.model.Alert
import com.example.guardband.ui.home.HomeViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Track tab (the default tab): the band's last known position on an
 * OpenStreetMap map, its status panel, and the bottom actions.
 *
 * View IDs, so the layout can be restyled without touching this file:
 * `track_map_view`, `track_online_badge`, `track_last_seen`, `track_battery`,
 * `track_empty_view`, `track_recenter_button`, `track_navigate_button`,
 * `track_layers_button`, `track_checkin_pill`, plus the top bar's
 * `tv_track_user_name`, `ib_track_notifications` and `ib_track_settings` and
 * the `ll_track_status` panel.
 *
 * Gear and bell forward to the shared [HomeViewModel], which asks the host to
 * show Settings or Notifications.
 *
 * **Recentring lives here, not in the ViewModel.** Where the camera is pointing
 * is view state, like a scroll offset: it survives nothing, means nothing to
 * the rest of the app, and [TrackViewModel] would have to model it only to hand
 * it straight back.
 *
 * Nothing here logs. The state carries the wearer's coordinates.
 */
class TrackFragment : Fragment(R.layout.fragment_track) {

    private lateinit var mapView: MapView
    private lateinit var tvUserName: TextView
    private lateinit var statusPanel: View
    private lateinit var tvOnlineBadge: TextView
    private lateinit var tvLastSeen: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnNavigate: Button

    private val viewModel: TrackViewModel by viewModels { TrackViewModel.Factory }
    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }

    /** The band's one marker, created on the first fix. */
    private var marker: Marker? = null

    /**
     * D4: whether new fixes still move the camera. True until the user pans or
     * zooms, and true again when they tap recenter - so the map follows the
     * band by default but never fights someone who is looking somewhere else.
     */
    private var followBand = true

    /** So a later fix does not keep yanking the zoom back. */
    private var hasZoomedToFix = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the layout inflates: the MapView is built in onCreateView, and
        // a tile request made with the wrong user agent comes back 403.
        OsmdroidConfig.apply(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView        = view.findViewById(R.id.track_map_view)
        tvUserName     = view.findViewById(R.id.tv_track_user_name)
        statusPanel    = view.findViewById(R.id.ll_track_status)
        tvOnlineBadge  = view.findViewById(R.id.track_online_badge)
        tvLastSeen     = view.findViewById(R.id.track_last_seen)
        tvBattery      = view.findViewById(R.id.track_battery)
        tvEmpty        = view.findViewById(R.id.track_empty_view)
        btnNavigate    = view.findViewById(R.id.track_navigate_button)

        setUpMap()

        view.findViewById<ImageButton>(R.id.ib_track_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }
        view.findViewById<ImageButton>(R.id.ib_track_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<Button>(R.id.track_checkin_pill)
            .setOnClickListener { viewModel.onCheckInClicked() }
        view.findViewById<FloatingActionButton>(R.id.track_layers_button)
            .setOnClickListener { viewModel.onLayersClicked() }
        view.findViewById<FloatingActionButton>(R.id.track_recenter_button)
            .setOnClickListener { onRecenter() }
        btnNavigate.setOnClickListener { viewModel.onNavigateClicked() }

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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    /**
     * osmdroid holds a tile cache, a worker and overlay references, so the
     * MapView has to be detached by hand or the Fragment's view leaks with it.
     */
    override fun onDestroyView() {
        mapView.onDetach()
        marker = null
        super.onDestroyView()
    }

    // ── Map ───────────────────────────────────────────────────────────────────

    private fun setUpMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        // The layout supplies its own round controls.
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

        // D5: somewhere sensible to look before the band has ever reported.
        mapView.controller.setZoom(DEFAULT_ZOOM)
        mapView.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))

        // D4. ACTION_MOVE rather than a MapListener, because a listener cannot
        // tell the user's pan from the camera moves this screen makes itself.
        // A tap gives DOWN and UP only, so tapping the map keeps following.
        mapView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE) followBand = false
            false
        }
    }

    /** D4: resume following, and move now. With no fix, go back to D5's view. */
    private fun onRecenter() {
        followBand = true

        val location = viewModel.uiState.value.location
        if (location == null) {
            mapView.controller.animateTo(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
            mapView.controller.setZoom(DEFAULT_ZOOM)
        } else {
            centerOn(location)
        }
    }

    private fun centerOn(location: Alert.Location) {
        mapView.controller.animateTo(GeoPoint(location.lat, location.lng))
        if (!hasZoomedToFix) {
            mapView.controller.setZoom(FIX_ZOOM)
            hasZoomedToFix = true
        }
    }

    /** One marker, moved rather than re-added (D4). */
    private fun showFix(location: Alert.Location) {
        val bandMarker = marker ?: Marker(mapView).also {
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            it.title = getString(R.string.label_track_marker_title)
            mapView.overlays.add(it)
            marker = it
        }

        bandMarker.position = GeoPoint(location.lat, location.lng)
        if (followBand) centerOn(location)
        mapView.invalidate()
    }

    private fun removeMarker() {
        marker?.let { mapView.overlays.remove(it) }
        marker = null
        mapView.invalidate()
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun render(state: TrackUiState) {
        tvUserName.text = state.userName

        renderStatusPanel(state)
        renderEmptyView(state)

        btnNavigate.isEnabled = state.navigateEnabled

        val location = state.location
        if (location != null) showFix(location) else removeMarker()
    }

    private fun renderStatusPanel(state: TrackUiState) {
        statusPanel.visibility = if (state.hasReported) View.VISIBLE else View.GONE
        if (!state.hasReported) return

        tvOnlineBadge.setText(
            if (state.isOnline) R.string.label_track_reporting
            else R.string.label_track_not_reporting
        )
        // DESIGN: Jul — the dot should take the reporting/not-reporting colour.

        val elapsed = state.elapsed
        tvLastSeen.visibility = if (elapsed == null) View.GONE else View.VISIBLE
        if (elapsed != null) tvLastSeen.text = lastSeenText(elapsed)

        tvBattery.text = batteryText(state.battery)
    }

    /**
     * [Elapsed] turned into text here rather than in the ViewModel, which holds
     * no Context and so cannot reach a `plurals`.
     */
    private fun lastSeenText(elapsed: Elapsed): String = when (elapsed) {
        is Elapsed.Seconds -> getString(R.string.label_track_last_seen_just_now)
        is Elapsed.Minutes -> quantity(R.plurals.label_track_last_seen_minutes, elapsed.value)
        is Elapsed.Hours -> quantity(R.plurals.label_track_last_seen_hours, elapsed.value)
        is Elapsed.Days -> quantity(R.plurals.label_track_last_seen_days, elapsed.value)
    }

    private fun quantity(resId: Int, value: Long): String =
        resources.getQuantityString(resId, value.toInt(), value.toInt())

    private fun batteryText(battery: Alert.Battery?): String = when {
        battery == null -> getString(R.string.label_track_battery_unknown)
        battery.isCharging ->
            getString(R.string.label_track_battery_charging, battery.percent)
        else -> getString(R.string.label_track_battery, battery.percent)
    }

    /**
     * Three different reasons there is no marker, which all look like an empty
     * map but mean different things to someone checking on a person: the read
     * failed, the band has never reported, or it is reporting without a fix.
     */
    private fun renderEmptyView(state: TrackUiState) {
        val message = when {
            state.isLoading -> null
            state.errorMessage != null -> state.errorMessage
            !state.hasReported -> getString(R.string.label_track_never_reported)
            !state.hasFix -> getString(R.string.label_track_no_fix)
            else -> null
        }

        tvEmpty.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) tvEmpty.text = message
    }

    // ── Events ────────────────────────────────────────────────────────────────

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
     * [ActivityNotFoundException], which is reported back through the ViewModel
     * so the wording stays with every other user-facing message rather than
     * being invented here.
     */
    private fun openNavigation(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            viewModel.onNavigationUnavailable()
        }
    }

    private companion object {
        /** D5: Cebu City, the default view before the band has ever reported. */
        const val DEFAULT_LAT = 10.3157
        const val DEFAULT_LNG = 123.8854
        const val DEFAULT_ZOOM = 13.0

        /** Street level, used once when the first fix arrives. */
        const val FIX_ZOOM = 16.0
    }
}
