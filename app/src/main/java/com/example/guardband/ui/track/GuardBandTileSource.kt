package com.example.guardband.ui.track

import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * The Track tab's OpenStreetMap tile source.
 *
 * **Why this exists instead of [org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK].**
 * Do not "simplify" this back to `MAPNIK` - the map goes blocked again, and the
 * reason is invisible in the log.
 *
 * `MAPNIK` is built with `TileSourcePolicy(2, 15)`, and 15 is all four flags,
 * including `FLAG_USER_AGENT_NORMALIZED`. With that flag set, osmdroid's
 * `TileDownloader` **ignores** [org.osmdroid.config.IConfigurationProvider.getUserAgentValue]
 * and sends `getNormalizedUserAgent()`, which is `packageName + "/" + versionCode`
 * - here `com.example.guardband/1`.
 *
 * `com.example.*` is the Android project-template placeholder, so thousands of
 * unrelated sample apps send it. OpenStreetMap measured millions of daily tile
 * requests they could not attribute to anything and **blocked the prefix**. The
 * block arrives as **HTTP 200 with a PNG** that has "access blocked" drawn into
 * the image, not as an error, so osmdroid renders it and caches it like a good
 * tile and nothing appears in the log.
 *
 * (Today `getNormalizedUserAgent()` happens to be null, because osmdroid only
 * computes it inside `Configuration.load(context, prefs)` and [OsmdroidConfig]
 * never calls that - so `userAgentValue` does reach the wire. That is luck, not
 * design: anyone adding a `load()` call would silently revert the user agent.
 * This source removes the flag so the outcome no longer depends on it.)
 *
 * This is the same server, the same tiles and the same attribution, with a
 * policy of `FLAG_NO_BULK or FLAG_NO_PREVENTIVE or FLAG_USER_AGENT_MEANINGFUL`
 * - every restriction `MAPNIK` carries **except** normalization - so
 * [OsmdroidConfig]'s user agent is what identifies the app, per OSM's usage
 * policy.
 *
 * **The name is deliberately not "Mapnik".** osmdroid's tile cache keys rows by
 * `(key, provider)`, where `provider` is this name, so a distinct name starts
 * with clean cache rows instead of re-serving the blocked images already stored
 * under `Mapnik`.
 *
 * Still not for production: see the note in [OsmdroidConfig].
 */
internal object GuardBandTileSource {

    // Declared ahead of the properties that use them: Kotlin's
    // initialization analysis rejects a forward reference inside an object
    // body even for a `const val`.

    /** Not "Mapnik": the tile cache is keyed by this. */
    private const val NAME = "GuardBandOsm"
    private const val BASE_URL = "https://tile.openstreetmap.org/"
    private const val COPYRIGHT = "\u00A9 OpenStreetMap contributors"
    private const val FILE_EXTENSION = ".png"
    private const val TILE_SIZE = 256
    private const val MIN_ZOOM = 0
    private const val MAX_ZOOM = 19

    /** MAPNIK's value. OSM's policy asks for no more than two connections. */
    private const val MAX_CONCURRENT = 2

    /** Every restriction MAPNIK has except `FLAG_USER_AGENT_NORMALIZED`. */
    private val POLICY = TileSourcePolicy(
        MAX_CONCURRENT,
        TileSourcePolicy.FLAG_NO_BULK or
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    )

    val OSM: XYTileSource = XYTileSource(
        NAME,
        MIN_ZOOM,
        MAX_ZOOM,
        TILE_SIZE,
        FILE_EXTENSION,
        arrayOf(BASE_URL),
        COPYRIGHT,
        POLICY,
    )
}
