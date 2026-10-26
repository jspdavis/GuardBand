package com.example.guardband.ui.track

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import com.example.guardband.BuildConfig
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

/**
 * The Track tab's tile source: MapTiler Cloud's raster tiles at 256 px, one
 * style per selectable base map ([MapLayer]; see [forLayer]). [STREETS] in
 * light mode and [STREETS_DARK] in dark ([forNightMode]; same key, same plan,
 * same credits), plus [SATELLITE], [OUTDOOR] and [TOPO], each under its own
 * cache name. It replaced OpenStreetMap's public tile server, whose usage
 * policy is written for light use, and it is the **only** place a MapTiler
 * URL, key, zoom range or credit is spelled out. See `docs/osmdroid.md`.
 *
 * **The key comes from `local.properties`** (`MAPTILER_API_KEY`) through
 * [BuildConfig.MAPTILER_API_KEY]. A build without one still compiles, with an
 * empty key; then [hasApiKey] is false, [getTileURLString] returns an empty
 * string (osmdroid's `TileDownloader` gives up on an empty URL before opening
 * a connection), and the Fragment turns the data connection off and says the
 * map is unavailable. There is **no fallback to tile.openstreetmap.org**.
 *
 * The key is in the URL, so the URL must never be logged. osmdroid only logs
 * it with `Configuration.isDebugMode` or `isDebugMapTileDownloader` on, which
 * nothing here sets; keep it that way.
 *
 * **The policy keeps every restriction `TileSourceFactory.MAPNIK` has except
 * `FLAG_USER_AGENT_NORMALIZED`.** With that flag osmdroid sends
 * `packageName/versionCode` instead of [OsmdroidConfig.USER_AGENT], and the
 * MapTiler key is restricted to that user agent, so a normalized agent would
 * get every tile refused. `FLAG_NO_BULK` and `FLAG_NO_PREVENTIVE` stay: the
 * map downloads what is on screen and nothing else.
 *
 * **The name is the cache key.** osmdroid's tile cache keys rows by
 * `(key, provider)`, where `provider` is the source's name, so these tiles
 * never mix with the OpenStreetMap ones cached under the old `GuardBandOsm`
 * source, and the dark tiles never mix with the light ones.
 *
 * The credits are [attribution]: MapTiler requires both, each linked, on
 * every map. A Free account must also show the MapTiler logo, linked to
 * [LOGO_URL]; `MapAttribution` binds it (see `docs/osmdroid.md`).
 */
internal class MapTilerTileSource private constructor(
    name: String,
    baseUrl: String,
    private val apiKey: String,
) : OnlineTileSourceBase(
    name,
    MIN_ZOOM,
    MAX_ZOOM,
    TILE_SIZE,
    FILE_EXTENSION,
    arrayOf(baseUrl),
    COPYRIGHT,
    POLICY,
) {

    override fun getTileURLString(pMapTileIndex: Long): String {
        if (apiKey.isEmpty()) return ""
        return baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) +
            imageFilenameEnding() +
            "?key=" + apiKey
    }

    companion object {

        // Declared ahead of the properties that use them, as in an object
        // body: a forward reference to a later property does not compile.

        /** Not "Mapnik" or "GuardBandOsm": the tile cache is keyed by this. */
        private const val NAME = "MapTilerStreets"
        private const val BASE_URL = "https://api.maptiler.com/maps/streets-v2/256/"

        /** The dark style's own cache rows, so a theme switch never shows stale light tiles. */
        private const val DARK_NAME = "MapTilerStreetsDark"
        private const val DARK_BASE_URL = "https://api.maptiler.com/maps/streets-v2-dark/256/"
        private const val FILE_EXTENSION = ".png"
        private const val TILE_SIZE = 256

        /** MapTiler's documented range for its standard maps' raster tiles. */
        private const val MIN_ZOOM = 0
        private const val MAX_ZOOM = 22

        /** The same two connections the OSM source used. */
        private const val MAX_CONCURRENT = 2

        /** Each credit MapTiler requires, with the page it must link to. */
        private val CREDITS = listOf(
            "© MapTiler" to "https://www.maptiler.com/copyright/",
            "© OpenStreetMap contributors" to "https://www.openstreetmap.org/copyright",
        )

        /** Where the MapTiler logo on the map links to, as the Free plan requires. */
        const val LOGO_URL = "https://www.maptiler.com/"

        private val COPYRIGHT = CREDITS.joinToString(" ") { it.first }

        /** Every restriction MAPNIK has except `FLAG_USER_AGENT_NORMALIZED`. */
        private val POLICY = TileSourcePolicy(
            MAX_CONCURRENT,
            TileSourcePolicy.FLAG_NO_BULK or
                TileSourcePolicy.FLAG_NO_PREVENTIVE or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
        )

        private val API_KEY = BuildConfig.MAPTILER_API_KEY.trim()

        /** False in a build made without `MAPTILER_API_KEY`. */
        val hasApiKey: Boolean = API_KEY.isNotEmpty()

        val STREETS      = MapTilerTileSource(NAME, BASE_URL, API_KEY)

        /** MapTiler's Streets Dark (checked 2026-10-20: served on this key and plan). */
        val STREETS_DARK = MapTilerTileSource(DARK_NAME, DARK_BASE_URL, API_KEY)

        /**
         * MapTiler's Satellite style — high-resolution satellite imagery with no
         * labels. (The labelled one is the separate `hybrid` style; this app
         * does not offer it.) Style id `satellite`, served on this key and plan.
         */
        val SATELLITE = MapTilerTileSource(
            "MapTilerSatellite",
            "https://api.maptiler.com/maps/satellite/256/",
            API_KEY,
        )

        /**
         * MapTiler's Outdoor style (`outdoor-v2`) — a hiking and outdoor map with
         * contour lines, terrain colouring and trail data. Verified against
         * MapTiler's `mapstyle.ts`, 2026-10-26: a v2-generation style, deprecated
         * in favour of `outdoor-v4` but still served (like [STREETS]).
         */
        val OUTDOOR = MapTilerTileSource(
            "MapTilerOutdoor",
            "https://api.maptiler.com/maps/outdoor-v2/256/",
            API_KEY,
        )

        /**
         * MapTiler's Topo style (`topo-v2`) — a topographic map with elevation
         * contours and hill shading. Verified against MapTiler's `mapstyle.ts`,
         * 2026-10-26: a v2-generation style, deprecated in favour of `topo-v4`
         * but still served (like [STREETS]). Named "Topo" in the UI.
         */
        val TOPO = MapTilerTileSource(
            "MapTilerTopo",
            "https://api.maptiler.com/maps/topo-v2/256/",
            API_KEY,
        )

        /**
         * The tile source for [MapLayer], as drawn in [night]: [STREETS] darkens
         * to [STREETS_DARK] in dark mode, and the other three stay the same in
         * either mode because they have no dark variant. The one place the
         * layers map to tile sources, so the Fragment never chooses one itself.
         */
        fun forLayer(layer: MapLayer, night: Boolean): MapTilerTileSource = when (layer) {
            MapLayer.AUTO -> forNightMode(night)
            MapLayer.STREET -> STREETS
            MapLayer.SATELLITE -> SATELLITE
        }

        /** The style for the theme the screen is drawn in. */
        fun forNightMode(night: Boolean): MapTilerTileSource = if (night) STREETS_DARK else STREETS

        /** The credits as text, each one a link to its copyright page. */
        fun attribution(): CharSequence {
            val text = SpannableStringBuilder()
            CREDITS.forEach { (credit, url) ->
                if (text.isNotEmpty()) text.append(' ')
                val start = text.length
                text.append(credit)
                text.setSpan(URLSpan(url), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return text
        }
    }
}
