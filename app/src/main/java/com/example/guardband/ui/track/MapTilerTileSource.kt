package com.example.guardband.ui.track

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import com.example.guardband.BuildConfig
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

/**
 * The Track tab's tile source: MapTiler Cloud's **Streets** style as 256 px
 * raster tiles. It replaced OpenStreetMap's public tile server, whose usage
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
 * `(key, provider)`, where `provider` is [NAME], so these tiles never mix with
 * the OpenStreetMap ones cached under the old `GuardBandOsm` source.
 *
 * The credits are [attribution]: MapTiler requires both, each linked, on
 * every map. A Free account must also show the MapTiler logo; that is still
 * open (see `docs/osmdroid.md`).
 */
internal class MapTilerTileSource private constructor(
    private val apiKey: String,
) : OnlineTileSourceBase(
    NAME,
    MIN_ZOOM,
    MAX_ZOOM,
    TILE_SIZE,
    FILE_EXTENSION,
    arrayOf(BASE_URL),
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

        val STREETS = MapTilerTileSource(API_KEY)

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
