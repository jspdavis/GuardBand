package com.example.guardband.ui.track

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.guardband.R
import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.AlertType
import com.example.guardband.ui.alert.AlertEvent
import com.example.guardband.ui.alert.AlertFormatting
import com.example.guardband.ui.alert.AlertRow
import com.example.guardband.ui.alert.AlertViewModel
import com.example.guardband.ui.home.HomeViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/**
 * Track tab (the default tab): the band's last known position on a map
 * (MapTiler tiles, see [MapTilerTileSource]), the round map controls, and the
 * recent-alerts sheet, a draggable bottom sheet over the map. The check-in
 * pill is the screen's one status line; the band status panel (reporting
 * badge, last seen, battery) was removed on 2026-10-14.
 *
 * View IDs, so the layout can be restyled without touching this file:
 * `track_root`, `track_map_view`, `track_empty_view`, `track_navigate_button`,
 * `track_layers_button`, `track_checkin_pill` (a read-only status, not a
 * button), `track_map_attribution` and `track_map_attribution_info` (the map
 * credits and their ⓘ; see [MapAttribution]), `track_map_logo` (MapTiler's
 * logo, bottom left, never collapsed; white on a dark map, dark on a light
 * one, see [applyLayer]), `space_track_sheet_peek`,
 * `track_recent_alerts_panel` (its own ids
 * are listed on [RecentAlertsPanel]), plus the top bar's `ib_track_settings`
 * (top-left) and `ib_track_notifications` (top-right), placed as on the
 * Contacts and Profile tabs.
 *
 * Gear and bell forward to the shared [HomeViewModel], which asks the host to
 * show Settings or Notifications; the sheet's "See all" does the same for the
 * Alert history screen. The sheet's rows come from the host-scoped
 * [AlertViewModel], the same instance the history screen reads. Track has no
 * avatar since 2026-10-19; the Profile tab is reached from the bar.
 *
 * **The band's marker is a face** (2026-10-19): a round photo or initials in
 * a white ring with a pointer, drawn by [BandMarkerIcon] from
 * [TrackUiState.markerFace]. It is drawn when the face changes, not on every
 * position; a new position only moves the marker, as before.
 *
 * **Recent alerts are pins** (2026-10-27): small markers in the type's colour
 * where each of [TrackUiState.alertPins] happened, all in one [FolderOverlay]
 * at the bottom of the overlays, so the face is always drawn over them and
 * takes the tap first. A tap on a pin opens its label
 * ([TrackUiState.pinLabel], drawn by [AlertPinLabelWindow]); a tap anywhere
 * else closes it, seen by a pass-through overlay on top of everything that
 * never consumes the tap, so the face keeps its own tap behaviour. The pins'
 * alerts are the sheet's: this Fragment hands [AlertViewModel]'s list to
 * [TrackViewModel.onAlertsChanged], and opens no listener of its own.
 *
 * **The round target button re-centres** (2026-10-27): a tap takes the map
 * back to the band's last known position and resumes following (D4),
 * zooming back in if the user had zoomed far out. It needs a fix, so it is
 * disabled without one. It used to open Google Maps on a tap (D6, removed
 * 2026-10-27) and re-centre on a long-press; its view id is still
 * `track_navigate_button`.
 *
 * **Recentring lives here, not in the ViewModel.** Where the camera is pointing
 * is view state, like a scroll offset: it survives nothing, means nothing to
 * the rest of the app, and [TrackViewModel] would have to model it only to hand
 * it straight back.
 *
 * Nothing here logs except one warning when the build has no MapTiler key.
 * The state carries the wearer's coordinates, and the tile URLs the key.
 */
class TrackFragment : Fragment(R.layout.fragment_track) {

    private lateinit var mapView: MapView
    private lateinit var tvEmpty: TextView
    private lateinit var btnNavigate: FloatingActionButton
    private lateinit var tvCheckIn: TextView
    private var cardCheckIn: View? = null
    private var ivCheckInStatus: ImageView? = null
    private var flCheckInIndicator: View? = null
    private var tvCheckInLabel: TextView? = null
    private lateinit var recentAlerts: RecentAlertsPanel
    private lateinit var attribution: MapAttribution
    private lateinit var mapLogo: ImageView
    private lateinit var markerIcon: BandMarkerIcon

    /** The marker's current picture, kept for a marker created after it arrives. */
    private var bandIcon: Drawable? = null

    private val viewModel: TrackViewModel by viewModels { TrackViewModel.Factory }
    private val homeViewModel: HomeViewModel by activityViewModels { HomeViewModel.Factory }

    /**
     * Host-scoped, so the panel and the Alert history screen share one
     * instance and one subscription. See [AlertViewModel].
     */
    private val alertViewModel: AlertViewModel by activityViewModels { AlertViewModel.Factory }

    /** The band's one marker, created on the first fix. */
    private var marker: Marker? = null

    /** The alert pins, one folder at the bottom of the overlays, under the face. */
    private var pinFolder: FolderOverlay? = null

    /** The pins on the map now, by `sequenceId`, so a label finds its pin. */
    private val pinMarkers = mutableMapOf<Long, Marker>()

    /** The pins last drawn, so an unchanged list draws nothing. Null after a new view. */
    private var appliedPins: List<AlertPin>? = null

    /** The label last shown, so an unchanged one is not reopened. */
    private var appliedPinLabel: AlertPinLabel? = null

    /** The one label window the pins share. */
    private var pinLabelWindow: AlertPinLabelWindow? = null

    /**
     * Sees every single tap before any marker does, never consumes it, and
     * tells the ViewModel the map was tapped, which closes an open label. A
     * pin's own tap then reopens its label. Always the top overlay; the face
     * is added under it ([addUnderTapWatcher]).
     */
    private var tapWatcher: Overlay? = null

    /**
     * D4: whether new fixes still move the camera. True until the user pans or
     * zooms, and true again when they tap the re-centre button - so the map follows the
     * band by default but never fights someone who is looking somewhere else.
     */
    private var followBand = true

    /** So a later fix does not keep yanking the zoom back. */
    private var hasZoomedToFix = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the layout inflates: the MapView is built in onCreateView, and
        // a tile requested with the wrong user agent comes back as a "blocked"
        // image that then sits in the cache. See OsmdroidConfig.
        OsmdroidConfig.apply(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView            = view.findViewById(R.id.track_map_view)
        tvEmpty            = view.findViewById(R.id.track_empty_view)
        btnNavigate        = view.findViewById(R.id.track_navigate_button)
        cardCheckIn        = view.findViewById(R.id.card_track_checkin)
        tvCheckIn          = view.findViewById(R.id.track_checkin_pill)
        ivCheckInStatus    = view.findViewById(R.id.iv_track_checkin_status)
        flCheckInIndicator = view.findViewById(R.id.fl_track_checkin_indicator)
        tvCheckInLabel     = view.findViewById(R.id.tv_track_checkin_label)

        recentAlerts = RecentAlertsPanel(
            view.findViewById(R.id.track_recent_alerts_panel),
            onSeeAll = { homeViewModel.onSeeAllAlertsClicked() },
            onRetry = { alertViewModel.onRetryClicked() }
        )

        // MapTiler's required credits: shown now, then collapsed to an ⓘ.
        // The logo beside them stays.
        mapLogo = view.findViewById(R.id.track_map_logo)
        attribution = MapAttribution(
            view.findViewById(R.id.track_map_attribution),
            view.findViewById(R.id.track_map_attribution_info),
            mapLogo
        )
        attribution.expand()

        markerIcon = BandMarkerIcon(requireContext(), viewLifecycleOwner) { icon ->
            bandIcon = icon
            marker?.let {
                it.icon = icon
                mapView.invalidate()
            }
        }

        setUpMap()

        view.findViewById<ImageButton>(R.id.ib_track_notifications)
            .setOnClickListener { homeViewModel.onNotificationsClicked() }
        view.findViewById<ImageButton>(R.id.ib_track_settings)
            .setOnClickListener { homeViewModel.onSettingsClicked() }
        view.findViewById<FloatingActionButton>(R.id.track_layers_button)
            .setOnClickListener { showLayerPicker() }
        btnNavigate.setOnClickListener { onRecenter() }

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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                alertViewModel.uiState.collect { state ->
                    recentAlerts.render(state)
                    // The pins come from the sheet's own list: no second listener.
                    viewModel.onAlertsChanged(state.history)
                }
            }
        }
        // Collected here as well as on the history screen: each event reaches
        // one collector, and without this one a failure notice would wait in
        // the channel until the history screen first opened, then show late.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                alertViewModel.events.collect(::handleAlertEvent)
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
        // Detaches every overlay too: the face, the pin folder and each pin.
        mapView.onDetach()
        pinLabelWindow?.onDetach()
        attribution.release()
        markerIcon.release()
        marker = null
        bandIcon = null
        pinFolder = null
        pinMarkers.clear()
        appliedPins = null
        appliedPinLabel = null
        pinLabelWindow = null
        tapWatcher = null
        super.onDestroyView()
    }

    // ── Map ───────────────────────────────────────────────────────────────────

    private fun setUpMap() {
        // Set even without a key, so the MapView's default (OSM's public
        // server) is never used. A theme change recreates the Fragment, which
        // applies the selected layer again (see [applyLayer]).
        if (!MapTilerTileSource.hasApiKey) {
            mapView.setUseDataConnection(false)
            Log.w(TAG, "MAPTILER_API_KEY is not set in local.properties; map tiles are disabled.")
        }
        mapView.setMultiTouchControls(true)
        // The layout supplies its own round controls.
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

        // D5: somewhere sensible to look before the band has ever reported.
        mapView.controller.setZoom(DEFAULT_ZOOM)
        mapView.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))

        // The selected base map (D7), applied now and again whenever it changes
        // (see render). Streets darkens with the theme; the other layers stay.
        applyLayer(viewModel.uiState.value.selectedLayer)

        // D4. ACTION_MOVE rather than a MapListener, because a listener cannot
        // tell the user's pan from the camera moves this screen makes itself.
        // A tap gives DOWN and UP only, so tapping the map keeps following.
        // A pan or a pinch is also what lets the credits collapse.
        mapView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE) {
                followBand = false
                attribution.collapse()
            }
            false
        }

        // The alert pins at the bottom, so everything else, the face above
        // all, is drawn over them and takes a tap first. The tap watcher at
        // the top. Taps go to the top overlay first.
        pinFolder = FolderOverlay().also { mapView.overlays.add(0, it) }
        pinLabelWindow = AlertPinLabelWindow(mapView) { viewModel.onMapTapped() }
        tapWatcher = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                viewModel.onMapTapped()
                return false
            }
        }.also { mapView.overlays.add(it) }
    }

    /** Adds [overlay] over everything already drawn, but under [tapWatcher]. */
    private fun addUnderTapWatcher(overlay: Overlay) {
        val at = mapView.overlays.indexOf(tapWatcher)
        if (at < 0) mapView.overlays.add(overlay) else mapView.overlays.add(at, overlay)
    }

    /**
     * The base map the state asks for ([TrackUiState.selectedLayer]). Streets
     * darkens with the theme; the other layers have no dark variant and stay
     * the same in either mode. Re-applied only when the layer changes (see
     * [render]) so a no-op re-render does not reload the tiles.
     *
     * The dark loading background is set only for Streets Dark: osmdroid's
     * loading grid is light grey, which would flash on the dark Streets style.
     * The other layers draw on a light grid, and tinting it would colour the
     * flash behind their loading tiles too. DESIGN: Jul
     *
     * The MapTiler logo follows the tiles, not the theme: the white-lettered
     * one on a dark style, the dark-lettered one on a light style
     * ([MapTilerTileSource.drawsDark]), since it has no chip behind it.
     */
    private var appliedLayer: MapLayer? = null

    private fun applyLayer(layer: MapLayer) {
        val tileSource = MapTilerTileSource.forLayer(layer, isNight())
        mapView.setTileSource(tileSource)
        mapView.setMinZoomLevel(tileSource.minimumZoomLevel.toDouble())
        mapView.setMaxZoomLevel(tileSource.maximumZoomLevel.toDouble())
        mapLogo.setImageResource(
            if (tileSource.drawsDark) R.drawable.logo_maptiler_white else R.drawable.logo_maptiler
        )
        if (tileSource === MapTilerTileSource.STREETS_DARK) {
            mapView.overlayManager.tilesOverlay.apply {
                loadingBackgroundColor = requireContext().getColor(R.color.surface_card)
                loadingLineColor = requireContext().getColor(R.color.divider)
            }
        }
        appliedLayer = layer
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * D7: the map type picker bottom sheet dialog.
     * Displays a bottom sheet with preview thumbnails for Auto, Street, Satellite, Outdoor, and Topo.
     * Tapping an option switches the tile source immediately and highlights the card.
     */
    private fun showLayerPicker() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(
            requireContext(),
            R.style.ThemeOverlay_GuardBand_BottomSheetDialog
        )
        val dialogView = layoutInflater.inflate(R.layout.dialog_map_type, null)
        dialog.setContentView(dialogView)

        dialogView.findViewById<View>(R.id.btn_close_map_type)?.setOnClickListener {
            dialog.dismiss()
        }

        updateMapTypeSelection(dialogView, viewModel.uiState.value.selectedLayer)

        dialogView.findViewById<View>(R.id.item_layer_auto)?.setOnClickListener {
            viewModel.onLayerSelected(MapLayer.AUTO)
            updateMapTypeSelection(dialogView, MapLayer.AUTO)
        }
        dialogView.findViewById<View>(R.id.item_layer_street)?.setOnClickListener {
            viewModel.onLayerSelected(MapLayer.STREET)
            updateMapTypeSelection(dialogView, MapLayer.STREET)
        }
        dialogView.findViewById<View>(R.id.item_layer_satellite)?.setOnClickListener {
            viewModel.onLayerSelected(MapLayer.SATELLITE)
            updateMapTypeSelection(dialogView, MapLayer.SATELLITE)
        }
        dialogView.findViewById<View>(R.id.item_layer_outdoor)?.setOnClickListener {
            viewModel.onLayerSelected(MapLayer.OUTDOOR)
            updateMapTypeSelection(dialogView, MapLayer.OUTDOOR)
        }
        dialogView.findViewById<View>(R.id.item_layer_topo)?.setOnClickListener {
            viewModel.onLayerSelected(MapLayer.TOPO)
            updateMapTypeSelection(dialogView, MapLayer.TOPO)
        }

        dialog.show()
    }

    private fun updateMapTypeSelection(dialogView: View, selectedLayer: MapLayer) {
        val brandGreen = requireContext().getColor(R.color.brand_primary)
        val textPrimary = requireContext().getColor(R.color.text_primary)
        val strokeWidth = (2.5f * resources.displayMetrics.density).toInt()

        val items = listOf(
            Triple(MapLayer.AUTO, R.id.card_outer_layer_auto, R.id.tv_layer_auto),
            Triple(MapLayer.STREET, R.id.card_outer_layer_street, R.id.tv_layer_street),
            Triple(MapLayer.SATELLITE, R.id.card_outer_layer_satellite, R.id.tv_layer_satellite),
            Triple(MapLayer.OUTDOOR, R.id.card_outer_layer_outdoor, R.id.tv_layer_outdoor),
            Triple(MapLayer.TOPO, R.id.card_outer_layer_topo, R.id.tv_layer_topo),
        )

        for ((layer, cardId, textId) in items) {
            val card = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(cardId) ?: continue
            val text = dialogView.findViewById<TextView>(textId) ?: continue
            val isSelected = layer == selectedLayer

            if (isSelected) {
                card.strokeWidth = strokeWidth
                card.setStrokeColor(android.content.res.ColorStateList.valueOf(brandGreen))
                text.setTextColor(brandGreen)
                text.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.poppins_semibold)
            } else {
                card.strokeWidth = 0
                card.setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT))
                text.setTextColor(textPrimary)
                text.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.poppins)
            }
        }
    }
    /**
     * D4, from a tap on the round re-centre button: back to the band's last
     * known position, and follow it again. Zoomed out past [FIX_ZOOM] - the
     * user wandered off to look at the wider area - it zooms back in to that
     * street-level view in the same animation; zoomed in closer, the zoom is
     * theirs and stays. With no fix, go back to D5's view (only reachable if
     * the button is ever left enabled without one; today it is disabled then).
     */
    private fun onRecenter() {
        followBand = true

        val location = viewModel.uiState.value.location
        if (location == null) {
            mapView.controller.animateTo(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
            mapView.controller.setZoom(DEFAULT_ZOOM)
        } else {
            val zoom = maxOf(mapView.zoomLevelDouble, FIX_ZOOM)
            mapView.controller.animateTo(
                GeoPoint(location.lat, location.lng), zoom, RECENTER_ANIMATION_MS
            )
            hasZoomedToFix = true
        }
    }

    private fun centerOn(location: Alert.Location) {
        mapView.controller.animateTo(GeoPoint(location.lat, location.lng))
        if (!hasZoomedToFix) {
            mapView.controller.setZoom(FIX_ZOOM)
            hasZoomedToFix = true
        }
    }

    /**
     * One marker, moved rather than re-added (D4). Its picture is set when it
     * is created and again only when [BandMarkerIcon] draws a new one; the
     * anchor is the pointer's tip, so the tip is on the band's position.
     */
    private fun showFix(location: Alert.Location) {
        val bandMarker = marker ?: Marker(mapView).also {
            bandIcon?.let { icon -> it.icon = icon }
            it.setAnchor(markerIcon.anchorU, markerIcon.anchorV)
            it.title = getString(R.string.label_track_marker_title)
            addUnderTapWatcher(it)
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
        // D7: a layer change swaps the tile source. Done before showFix so the
        // marker redraws on the new base map; applied only on change so a
        // position-only update never reloads the tiles.
        if (state.selectedLayer != appliedLayer) applyLayer(state.selectedLayer)
        renderEmptyView(state)
        // Before showFix, so a marker created now gets the face. Draws nothing
        // when the face is the same as last time.
        markerIcon.bind(state.markerFace)
        renderCheckIn(state.checkIn)

        btnNavigate.isEnabled = state.navigateEnabled

        val location = state.location
        if (location != null) showFix(location) else removeMarker()

        // Pins before the label, which opens on one of them.
        renderPins(state.alertPins)
        renderPinLabel(state.pinLabel)
    }

    /**
     * Redraws the pins when the list changes, and only then: a walking band
     * moves the face every few seconds without touching them. All of them are
     * replaced rather than diffed - at most ten, and only when an alert
     * arrives - so a pin that dropped out can never be left behind. The
     * oldest is added first, so where two overlap on screen the newest is on
     * top. The open label is closed with its pin and reopened on the new one
     * by [renderPinLabel].
     */
    private fun renderPins(pins: List<AlertPin>) {
        if (pins == appliedPins) return
        val folder = pinFolder ?: return

        pinLabelWindow?.close()
        appliedPinLabel = null
        pinMarkers.values.forEach {
            folder.remove(it)
            it.onDetach(mapView)
        }
        pinMarkers.clear()

        for (pin in pins.asReversed()) {
            val icon = pinIcon(pin.type) ?: continue
            val pinMarker = Marker(mapView).apply {
                position = GeoPoint(pin.lat, pin.lng)
                this.icon = ContextCompat.getDrawable(requireContext(), icon)
                setAnchor(PIN_ANCHOR_U, PIN_ANCHOR_V)
                // The label sits just above the pin's head, not over it.
                setInfoWindow(pinLabelWindow)
                setInfoWindowAnchor(PIN_ANCHOR_U, 0f)
                // osmdroid's default tap pans to the marker; a pin only opens its label.
                setPanToView(false)
                setOnMarkerClickListener { _, _ ->
                    viewModel.onAlertPinTapped(pin.alert)
                    true
                }
            }
            folder.add(pinMarker)
            pinMarkers[pin.alert.sequenceId] = pinMarker
        }
        appliedPins = pins
        mapView.invalidate()
    }

    /**
     * Opens, moves, re-words or closes the one label, as the state says. The
     * band's own "Your band" bubble is closed when a pin's opens, so only
     * one label is ever over the map.
     */
    private fun renderPinLabel(label: AlertPinLabel?) {
        if (label == appliedPinLabel) return
        appliedPinLabel = label

        val pinMarker = label?.let { pinMarkers[it.pin.alert.sequenceId] }
        if (label == null || pinMarker == null) {
            pinLabelWindow?.close()
            return
        }
        marker?.closeInfoWindow()
        pinMarker.title = pinLabelText(label)
        pinMarker.showInfoWindow()
    }

    /** "Panic, 5 min ago": the alert rows' own type name and time wording. */
    private fun pinLabelText(label: AlertPinLabel): String = getString(
        R.string.label_track_alert_pin,
        getString(AlertFormatting.typeLabel(label.pin.type)),
        AlertFormatting.relativeTime(requireContext(), AlertRow(label.pin.alert, elapsed = label.elapsed))
    )

    /** Each pinned type's pin: its colour and its symbol. Never null for a type the selector pins. */
    @DrawableRes
    private fun pinIcon(type: AlertType): Int? = when (type) {
        AlertType.PANIC -> R.drawable.ic_alert_pin_panic
        AlertType.CHECKIN -> R.drawable.ic_alert_pin_checkin
        AlertType.LOW_BATTERY -> R.drawable.ic_alert_pin_low_battery
        AlertType.TRACKING_UPDATE -> null
    }

    /**
     * Binds the dedicated last check-in status indicator, label, and timestamp.
     */
    private fun renderCheckIn(status: CheckInStatus) {
        val context = context ?: return
        tvCheckInLabel?.text = getString(R.string.label_track_checkin_header)
        tvCheckIn.text = checkInText(status)

        val (iconRes, colorRes) = when (status) {
            is CheckInStatus.Received -> Pair(R.drawable.ic_check_circle, R.color.alert_checkin)
            CheckInStatus.Loading -> Pair(R.drawable.ic_history, R.color.text_hint)
            CheckInStatus.None -> Pair(R.drawable.ic_info, R.color.text_secondary)
            CheckInStatus.Unavailable -> Pair(R.drawable.ic_warning, R.color.rule_unmet)
        }

        ivCheckInStatus?.let { iv ->
            iv.setImageResource(iconRes)
            iv.imageTintList = ColorStateList.valueOf(context.getColor(colorRes))
        }

        flCheckInIndicator?.let { fl ->
            fl.backgroundTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(context.getColor(colorRes), 0x29)
            )
        }

        cardCheckIn?.contentDescription =
            "${getString(R.string.label_track_checkin_header)}: ${tvCheckIn.text}"
    }

    /**
     * The pill's text; a status, so there is no click to wire. [Elapsed] is
     * turned into text here rather than in the ViewModel, which holds no
     * Context and so cannot reach a `plurals`.
     */
    private fun checkInText(status: CheckInStatus): String = when (status) {
        CheckInStatus.Loading -> getString(R.string.label_track_checkin_loading)
        CheckInStatus.None -> getString(R.string.label_track_checkin_none)
        CheckInStatus.Unavailable -> getString(R.string.label_track_checkin_unavailable)
        is CheckInStatus.Received -> when (val elapsed = status.elapsed) {
            null -> getString(R.string.label_track_checkin_time_unknown)
            is Elapsed.Seconds -> getString(R.string.label_alert_just_now)
            is Elapsed.Minutes -> quantity(R.plurals.label_alert_minutes_ago, elapsed.value)
            is Elapsed.Hours -> quantity(R.plurals.label_alert_hours_ago, elapsed.value)
            is Elapsed.Days -> quantity(R.plurals.label_track_checkin_days, elapsed.value)
        }
    }

    private fun quantity(resId: Int, value: Long): String =
        resources.getQuantityString(resId, value.toInt(), value.toInt())

    /**
     * Three different reasons there is no marker, which all look like an empty
     * map but mean different things to someone checking on a person: the read
     * failed, the band has never reported, or it is reporting without a fix.
     */
    private fun renderEmptyView(state: TrackUiState) {
        val message = when {
            // A build without a MapTiler key: no tiles will ever load.
            !MapTilerTileSource.hasApiKey -> getString(R.string.label_track_map_unavailable)
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
        }
    }

    private fun handleAlertEvent(event: AlertEvent) {
        when (event) {
            is AlertEvent.ShowMessage ->
                Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "TrackFragment"

        /** D5: Cebu City, the default view before the band has ever reported. */
        const val DEFAULT_LAT = 10.3157
        const val DEFAULT_LNG = 123.8854
        const val DEFAULT_ZOOM = 13.0

        /**
         * Street level: used once when the first fix arrives, and as the
         * zoom a re-centre comes back to when the user had zoomed out past it.
         */
        const val FIX_ZOOM = 16.0

        /** How long the re-centre's pan and zoom take. */
        const val RECENTER_ANIMATION_MS = 600L

        /**
         * An alert pin's anchor, its tip: centred, and (12, 30) of the
         * drawables' 24 x 32 viewport (`ic_alert_pin_*.xml`), so the tip is
         * on the alert's coordinates.
         */
        const val PIN_ANCHOR_U = 0.5f
        const val PIN_ANCHOR_V = 30f / 32f
    }
}
