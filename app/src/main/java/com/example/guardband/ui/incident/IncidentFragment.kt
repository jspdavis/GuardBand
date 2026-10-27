package com.example.guardband.ui.incident

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import com.example.guardband.ui.alert.AlertFormatting
import com.example.guardband.ui.alert.AlertRow
import com.example.guardband.ui.track.Elapsed
import com.example.guardband.ui.track.MapAttribution
import com.example.guardband.ui.track.MapTilerMap
import com.example.guardband.ui.track.MapTilerTileSource
import com.example.guardband.ui.track.OsmdroidConfig
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Incident view (2026-10-27): one panic on a map with the FR-09 tracking
 * updates that followed it drawn as a path, and a summary underneath. A
 * pushed screen in the Home host; the host adds a new one for each panic
 * ([newInstance]) and removes it on Back, which returns to whatever opened
 * it (Track or Alert history).
 *
 * **The map is Track's, set up the same way**: [OsmdroidConfig] in
 * [onCreate], before the `MapView` inflates; [MapTilerMap] for the no-key
 * guard, the base map Track last used, the dark map in dark mode and the
 * logo; [MapAttribution] for the credits. Its overlays, bottom to top: the
 * path ([Polyline], from the panic through every tracked position in
 * `sequenceId` order), the panic's pin, and the end marker at the newest
 * tracked position. None has a bubble; the summary says what they are.
 *
 * The map fits the path once, when the incident is first drawn; later
 * updates extend the path without moving the camera, so a guardian who has
 * panned is not pulled back. Nothing here logs: the state is a path of the
 * wearer's positions.
 *
 * View IDs: ib_incident_back, tv_incident_title, cl_incident_map,
 * incident_map_view, incident_map_empty, incident_map_attribution,
 * incident_map_attribution_info, incident_map_logo, incident_progress,
 * incident_error_view, incident_error_message, incident_retry_button,
 * incident_summary, iv_incident_icon, tv_incident_type, tv_incident_time,
 * ll_incident_place, tv_incident_place, ll_incident_battery,
 * iv_incident_battery_icon, tv_incident_battery, tv_incident_updates,
 * tv_incident_latest_update, tv_incident_no_updates.
 */
class IncidentFragment : Fragment(R.layout.fragment_incident) {

    private val panicSequenceId: Long by lazy {
        requireArguments().getLong(ARG_PANIC_SEQUENCE_ID)
    }

    private val viewModel: IncidentViewModel by viewModels {
        IncidentViewModel.factory(panicSequenceId)
    }

    private lateinit var mapView: MapView
    private lateinit var mapEmpty: TextView
    private lateinit var mapLogo: ImageView
    private lateinit var attribution: MapAttribution
    private lateinit var progress: View
    private lateinit var errorView: View
    private lateinit var tvError: TextView
    private lateinit var summary: View
    private lateinit var ivIcon: ImageView
    private lateinit var tvTime: TextView
    private lateinit var placeRow: View
    private lateinit var tvPlace: TextView
    private lateinit var batteryRow: View
    private lateinit var ivBattery: ImageView
    private lateinit var tvBattery: TextView
    private lateinit var tvUpdates: TextView
    private lateinit var tvLatestUpdate: TextView
    private lateinit var tvNoUpdates: TextView

    private var path: Polyline? = null
    private var panicMarker: Marker? = null
    private var endMarker: Marker? = null

    /** The incident last drawn on the map, so an unchanged one draws nothing. */
    private var drawnIncident: Incident? = null

    /** Whether the camera has been fitted to the path for this view yet. */
    private var hasFitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the layout inflates the MapView: see OsmdroidConfig.
        OsmdroidConfig.apply(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.incident_map_view)
        mapEmpty = view.findViewById(R.id.incident_map_empty)
        mapLogo = view.findViewById(R.id.incident_map_logo)
        progress = view.findViewById(R.id.incident_progress)
        errorView = view.findViewById(R.id.incident_error_view)
        tvError = view.findViewById(R.id.incident_error_message)
        summary = view.findViewById(R.id.incident_summary)
        ivIcon = view.findViewById(R.id.iv_incident_icon)
        tvTime = view.findViewById(R.id.tv_incident_time)
        placeRow = view.findViewById(R.id.ll_incident_place)
        tvPlace = view.findViewById(R.id.tv_incident_place)
        batteryRow = view.findViewById(R.id.ll_incident_battery)
        ivBattery = view.findViewById(R.id.iv_incident_battery_icon)
        tvBattery = view.findViewById(R.id.tv_incident_battery)
        tvUpdates = view.findViewById(R.id.tv_incident_updates)
        tvLatestUpdate = view.findViewById(R.id.tv_incident_latest_update)
        tvNoUpdates = view.findViewById(R.id.tv_incident_no_updates)

        view.findViewById<View>(R.id.ib_incident_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<View>(R.id.incident_retry_button).setOnClickListener {
            viewModel.onRetryClicked()
        }

        attribution = MapAttribution(
            view.findViewById(R.id.incident_map_attribution),
            view.findViewById(R.id.incident_map_attribution_info),
            mapLogo
        )
        attribution.expand()

        bindPanicBadge()
        setUpMap()

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

    /** osmdroid holds a tile cache, a worker and the overlays: detached by hand, as on Track. */
    override fun onDestroyView() {
        mapView.onDetach()
        attribution.release()
        path = null
        panicMarker = null
        endMarker = null
        drawnIncident = null
        hasFitted = false
        super.onDestroyView()
    }

    // ── Map ───────────────────────────────────────────────────────────────────

    private fun setUpMap() {
        MapTilerMap.prepare(mapView, TAG)
        MapTilerMap.applyLayer(mapView, mapLogo, viewModel.uiState.value.layer)
        mapView.controller.setZoom(DEFAULT_ZOOM)
        mapView.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
        // A pan or a pinch lets the credits collapse, as on Track.
        mapView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE) attribution.collapse()
            false
        }

        val density = resources.displayMetrics.density
        path = Polyline(mapView).apply {
            setInfoWindow(null)
            outlinePaint.color = requireContext().getColor(R.color.alert_tracking)
            outlinePaint.strokeWidth = PATH_WIDTH_DP * density
        }.also { mapView.overlays.add(it) }
        panicMarker = Marker(mapView).apply {
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_alert_pin_panic)
            setAnchor(PIN_ANCHOR_U, PIN_ANCHOR_V)
            setInfoWindow(null)
            setPanToView(false)
            isEnabled = false
        }.also { mapView.overlays.add(it) }
        endMarker = Marker(mapView).apply {
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_incident_end)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
            setPanToView(false)
            isEnabled = false
        }.also { mapView.overlays.add(it) }
    }

    /**
     * Draws [incident] when it changed: the panic's pin, the path from it
     * through every tracked position, and the end marker at the newest. With
     * no tracked position there is no path and no end marker, only the pin.
     */
    private fun drawIncident(incident: Incident) {
        if (incident == drawnIncident) return
        drawnIncident = incident

        val panicPoint = incident.panicLocation?.toGeoPoint()
        panicMarker?.apply {
            if (panicPoint != null) position = panicPoint
            isEnabled = panicPoint != null
        }

        val tracked = incident.trackedLocations.map { it.toGeoPoint() }
        path?.setPoints(if (tracked.isEmpty()) emptyList() else incident.path.map { it.toGeoPoint() })
        endMarker?.apply {
            tracked.lastOrNull()?.let { position = it }
            isEnabled = tracked.isNotEmpty()
        }
        mapView.invalidate()

        if (!hasFitted) {
            val points = incident.path.map { it.toGeoPoint() }
            if (points.isNotEmpty()) {
                hasFitted = true
                whenLaidOut { fitTo(points) }
            }
        }
    }

    /** Runs [action] once the MapView has a size: zooming to a box needs one. */
    private fun whenLaidOut(action: () -> Unit) {
        if (mapView.isLayoutOccurred) action()
        else mapView.addOnFirstLayoutListener { _, _, _, _, _ -> action() }
    }

    /**
     * The whole path in view, with a margin. One point (a panic with no
     * tracked position yet) is shown at street level. Never closer than
     * [MAX_FIT_ZOOM], so a band standing still is not shown at building
     * scale.
     */
    private fun fitTo(points: List<GeoPoint>) {
        if (points.size == 1) {
            mapView.controller.setZoom(SINGLE_POINT_ZOOM)
            mapView.controller.setCenter(points.single())
            return
        }
        val border = (FIT_BORDER_DP * resources.displayMetrics.density).toInt()
        mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), false, border)
        if (mapView.zoomLevelDouble > MAX_FIT_ZOOM) mapView.controller.setZoom(MAX_FIT_ZOOM)
    }

    private fun Alert.Location.toGeoPoint() = GeoPoint(lat, lng)

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun render(state: IncidentUiState) {
        progress.isVisible = state.isLoading && state.incident == null
        errorView.isVisible = state.errorMessage != null
        state.errorMessage?.let { tvError.text = it }

        val incident = state.incident
        summary.isVisible = incident != null
        renderMapEmpty(incident)
        if (incident == null) return

        drawIncident(incident)
        renderSummary(state, incident)
    }

    /** Why the map shows nothing: no key in this build, or nothing to place. */
    private fun renderMapEmpty(incident: Incident?) {
        val message = when {
            !MapTilerTileSource.hasApiKey -> getString(R.string.label_track_map_unavailable)
            incident != null && incident.path.isEmpty() -> getString(R.string.label_incident_no_position)
            else -> null
        }
        mapEmpty.isVisible = message != null
        if (message != null) mapEmpty.text = message
    }

    private fun renderSummary(state: IncidentUiState, incident: Incident) {
        val context = requireContext()
        val panic = incident.panic
        tvTime.text = timeText(panic, state.panicElapsed)

        val location = panic.location
        placeRow.isVisible = location != null
        if (location != null) tvPlace.text = AlertFormatting.locationText(context, location, state.placeName)

        // The latest reading the incident holds, not only the panic's.
        val battery = incident.latestBattery
        batteryRow.isVisible = battery != null
        if (battery != null) {
            ivBattery.setImageResource(AlertFormatting.batteryIcon(battery))
            ivBattery.imageTintList =
                ColorStateList.valueOf(context.getColor(AlertFormatting.batteryColor(battery)))
            tvBattery.text = getString(
                if (battery.isCharging) R.string.label_alert_battery_level_charging
                else R.string.label_alert_battery_level,
                battery.percent
            )
            tvBattery.contentDescription = getString(
                if (battery.isCharging) R.string.label_alert_battery_charging
                else R.string.label_alert_battery,
                battery.percent
            )
        }

        val count = state.updateCount
        tvUpdates.isVisible = count > 0
        tvUpdates.text = resources.getQuantityString(R.plurals.label_incident_updates, count, count)
        val latest = state.latestUpdate
        tvLatestUpdate.isVisible = latest != null
        if (latest != null) {
            tvLatestUpdate.text = getString(
                R.string.label_incident_latest_update,
                AlertFormatting.relativeTime(context, AlertRow(latest, elapsed = state.latestUpdateElapsed)),
                AlertFormatting.clockTime(latest.timestamp)
            )
        }
        tvNoUpdates.isVisible = state.showsNoUpdates
    }

    /**
     * "5 min ago · Oct 27, 3:04 PM". From a day on, the relative time is
     * already the date, so it is shown once.
     */
    private fun timeText(alert: Alert, elapsed: Elapsed?): String {
        val absolute = AlertFormatting.formatTimestamp(alert.timestamp)
        if (elapsed == null || elapsed is Elapsed.Days) return absolute
        val relative = AlertFormatting.relativeTime(requireContext(), AlertRow(alert, elapsed = elapsed))
        return getString(R.string.label_incident_time, relative, absolute)
    }

    /** The panic's symbol on the alert cards' pale badge. */
    private fun bindPanicBadge() {
        val color = requireContext().getColor(R.color.alert_panic)
        ivIcon.imageTintList = ColorStateList.valueOf(color)
        ivIcon.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, BADGE_ALPHA))
    }

    private fun handleEvent(event: IncidentEvent) {
        when (event) {
            is IncidentEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "IncidentFragment"
        private const val ARG_PANIC_SEQUENCE_ID = "panic_sequence_id"

        /** Cebu City, before the incident is read; as on Track. */
        private const val DEFAULT_LAT = 10.3157
        private const val DEFAULT_LNG = 123.8854
        private const val DEFAULT_ZOOM = 13.0

        /** A lone panic point, at Track's street level. */
        private const val SINGLE_POINT_ZOOM = 16.0

        /** The closest a fitted path is shown. */
        private const val MAX_FIT_ZOOM = 18.0

        /** The margin around a fitted path. DESIGN: Jul */
        private const val FIT_BORDER_DP = 48

        /** The path's width. DESIGN: Jul */
        private const val PATH_WIDTH_DP = 4f

        /** The badge's shade, as on the alert cards. DESIGN: Jul */
        private const val BADGE_ALPHA = 0x29

        /** The panic pin's tip, as on Track: (12, 30) of its 24 x 32 viewport. */
        private const val PIN_ANCHOR_U = 0.5f
        private const val PIN_ANCHOR_V = 30f / 32f

        /** The Incident view of the panic numbered [panicSequenceId]. Only the host calls this. */
        fun newInstance(panicSequenceId: Long) = IncidentFragment().apply {
            arguments = bundleOf(ARG_PANIC_SEQUENCE_ID to panicSequenceId)
        }
    }
}
