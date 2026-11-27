package com.example.guardband.ui.track

import android.content.res.Configuration
import android.util.Log
import android.widget.ImageView
import com.example.guardband.R
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * How a GuardBand map is set up, for every screen that shows one: Track and
 * the Incident view. One place, so the two cannot drift on the no-key guard,
 * the base map, the dark loading grid or the logo. It configures a `MapView`
 * only; [OsmdroidConfig] (the user agent and the cache) still runs first, in
 * each Fragment's `onCreate`, and the tile policy stays in
 * [MapTilerTileSource].
 */
internal object MapTilerMap {

    /**
     * Without a key: no data connection at all, so the `MapView`'s default
     * (OSM's public server) is never asked, and one warning. Pinch to zoom; no
     * stock zoom buttons, since each screen has its own controls or none.
     *
     * **The memory tile cache is told to be strict on a low-RAM phone**
     * (2026-11-24). osmdroid's cache capacity only ever grows - it ends up at
     * the most tiles any one view of the session needed - and garbage
     * collection removes off-screen tiles only once the cache is over that
     * capacity. On a phone Android itself calls low-RAM, each 256 px tile is
     * a quarter of a megabyte it cannot spare, so every tile that leaves the
     * screen goes with it. [OsmdroidConfig] drops that phone's off-screen
     * buffer to match; see its KDoc.
     */
    fun prepare(mapView: MapView, logTag: String) {
        if (!MapTilerTileSource.hasApiKey) {
            mapView.setUseDataConnection(false)
            Log.w(logTag, "MAPTILER_API_KEY is not set in local.properties; map tiles are disabled.")
        }
        mapView.setMultiTouchControls(true)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.tileProvider.tileCache
            .setStressedMemory(OsmdroidConfig.isLowRamDevice(mapView.context))
    }

    /**
     * Sets [layer]'s tiles (Streets darkens with the theme; the other layers
     * have no dark variant), the zoom range, and the MapTiler logo that suits
     * them: white lettering on a dark style, dark on a light one
     * ([MapTilerTileSource.drawsDark]), since it has no chip behind it.
     *
     * The dark loading background is set only for Streets Dark: osmdroid's
     * loading grid is light grey, which would flash on the dark Streets style.
     * The other layers draw on a light grid, and tinting it would colour the
     * flash behind their loading tiles too. DESIGN: Jul
     */
    fun applyLayer(mapView: MapView, logo: ImageView, layer: MapLayer) {
        val context = mapView.context
        val tileSource = MapTilerTileSource.forLayer(layer, isNight(mapView))
        mapView.setTileSource(tileSource)
        mapView.setMinZoomLevel(tileSource.minimumZoomLevel.toDouble())
        mapView.setMaxZoomLevel(tileSource.maximumZoomLevel.toDouble())
        logo.setImageResource(
            if (tileSource.drawsDark) R.drawable.logo_maptiler_white else R.drawable.logo_maptiler
        )
        if (tileSource === MapTilerTileSource.STREETS_DARK) {
            mapView.overlayManager.tilesOverlay.apply {
                loadingBackgroundColor = context.getColor(R.color.surface_card)
                loadingLineColor = context.getColor(R.color.divider)
            }
        }
    }

    private fun isNight(mapView: MapView): Boolean =
        (mapView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
