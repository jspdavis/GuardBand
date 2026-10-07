package com.example.guardband.ui.track

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * osmdroid's process-wide setup, in one place (D1).
 *
 * Two things have to be right before a single tile is requested:
 *
 *  - **The user agent.** OpenStreetMap's tile servers reject the default
 *    (and anything that looks like a library default) with HTTP 403, so the
 *    map would come up blank with no obvious cause. The package name is what
 *    their usage policy asks for.
 *  - **The cache path.** Pointing both the base path and the tile cache at
 *    [Context.getCacheDir] keeps everything in app-specific storage, which
 *    needs **no storage permission** on any API level and is removed with the
 *    app. osmdroid's own default wants external storage.
 *
 * Call this **before the layout is inflated** - the `MapView` is constructed
 * during `onCreateView`, so the Fragment does it in `onCreate`.
 *
 * Note for anything beyond this academic build: OpenStreetMap's public tile
 * servers are a volunteer-funded service with a usage policy intended for
 * light use. A production app would need its own tile source or a commercial
 * provider.
 */
internal object OsmdroidConfig {

    fun apply(context: Context) {
        val configuration = Configuration.getInstance()

        // Identifies this app to the tile server, per OSM's usage policy.
        configuration.userAgentValue = context.packageName

        val base = File(context.cacheDir, CACHE_DIR)
        configuration.osmdroidBasePath = base
        configuration.osmdroidTileCache = File(base, TILE_CACHE_DIR)
    }

    private const val CACHE_DIR = "osmdroid"
    private const val TILE_CACHE_DIR = "tiles"
}
