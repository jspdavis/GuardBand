package com.example.guardband.ui.track

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * osmdroid's process-wide setup, in one place (D1).
 *
 * Two things have to be right before a single tile is requested:
 *
 *  - **The user agent must be unique to this app.** It was first fixed for
 *    OpenStreetMap's usage policy, which requires a "proper, unique and
 *    identifiable" User-Agent and blocks identifiers that thousands of apps
 *    share - notably `osmdroid` (the library default) and the `com.example.*`
 *    package prefix the Android project template generates, which is still
 *    this app's `applicationId`. The tiles now come from MapTiler, whose key
 *    is restricted to this exact agent, so it matters more, not less. So
 *    [USER_AGENT] is a fixed string naming the app and where to find it,
 *    **not** `context.packageName`. See [MapTilerTileSource] for the other
 *    half of this - the tile source must also be one that does not override
 *    this value.
 *  - **The cache path.** Pointing both the base path and the tile cache at
 *    [Context.getCacheDir] keeps everything in app-specific storage, which
 *    needs **no storage permission** on any API level and is removed with the
 *    app. osmdroid's own default wants external storage.
 *
 * Call this **before the layout is inflated** - the `MapView` is constructed
 * during `onCreateView`, so the Fragment does it in `onCreate`.
 *
 * **Never call `Configuration.getInstance().load(context, prefs)`.** That is
 * the only thing that computes osmdroid's "normalized" user agent
 * (`packageName/versionCode`), which a tile source carrying
 * `FLAG_USER_AGENT_NORMALIZED` would then send instead of [USER_AGENT] -
 * putting `com.example.guardband/1` back on the wire, which the MapTiler key
 * would refuse. [MapTilerTileSource] drops that flag, so the two guards are
 * independent.
 *
 * **Never turn on `isDebugMode` or `isDebugMapTileDownloader`.** osmdroid then
 * logs every tile URL, and each one carries the MapTiler key.
 */
internal object OsmdroidConfig {

    /**
     * Identifies this app to the tile server: unique, and it leads somewhere
     * a server operator can reach us.
     *
     * Deliberately **not** derived from the package name or the version, so
     * that a rename or a version bump cannot change what a server that
     * filters by string sees. **The MapTiler key's "Allowed user-agent
     * header" restriction is set to this value:** change it in the MapTiler
     * dashboard first, or every tile is refused.
     */
    const val USER_AGENT = "GuardBand/1.0 (+https://github.com/jspdavis/GuardBand)"

    fun apply(context: Context) {
        val configuration = Configuration.getInstance()

        configuration.userAgentValue = USER_AGENT

        val base = File(context.cacheDir, CACHE_DIR)
        configuration.osmdroidBasePath = base
        configuration.osmdroidTileCache = File(base, TILE_CACHE_DIR)
    }

    private const val CACHE_DIR = "osmdroid"
    private const val TILE_CACHE_DIR = "tiles"
}
