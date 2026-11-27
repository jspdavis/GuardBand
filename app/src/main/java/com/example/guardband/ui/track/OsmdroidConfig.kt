package com.example.guardband.ui.track

import android.app.ActivityManager
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
 * **The cache budget is ours, not osmdroid's** (2026-11-24). osmdroid defaults
 * to **600 MB** of tiles with a 500 MB trim target, which is a lot of a small
 * phone's storage to hand to a map that only ever shows one city. [apply]
 * replaces it with a share of what is actually free under `cacheDir`, clamped
 * between [MIN_DISK_CACHE_BYTES] and [MAX_DISK_CACHE_BYTES], so a nearly full
 * phone keeps a usable map and a roomy one still never fills up. The figure is
 * computed once per process: `usableSpace` is a filesystem call, and both
 * Track and the Incident view call this.
 *
 * [Configuration.setCacheMapTileOvershoot] is the **memory** cache's buffer,
 * not a pre-fetch: it is how many tiles that have just left the screen survive
 * garbage collection, so panning back costs nothing. `FLAG_NO_PREVENTIVE` is
 * still on, so no tile is ever requested for an area the user is not looking
 * at. A low-RAM device gets none of that buffer.
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

    /** The least tile storage worth keeping: a few screenfuls at street level. */
    const val MIN_DISK_CACHE_BYTES = 16L * 1024 * 1024

    /** The most we ever take, however much is free. */
    const val MAX_DISK_CACHE_BYTES = 64L * 1024 * 1024

    /** The share of the free space under `cacheDir` the tiles may use. */
    const val FREE_SPACE_SHARE = 20

    /** Trimming stops here, so the trim has room before the next write hits the cap. */
    const val TRIM_PERCENT = 75

    /**
     * Tiles kept after they leave the screen, on a phone with memory to spare:
     * roughly a half-screen ring at 256 px, about 3 MB.
     */
    const val MEMORY_TILE_OVERSHOOT = 12

    /** The cache budget is a filesystem read, so it is computed once per process. */
    private var budgetApplied = false

    fun apply(context: Context) {
        val configuration = Configuration.getInstance()

        configuration.userAgentValue = USER_AGENT

        val base = File(context.cacheDir, CACHE_DIR)
        configuration.osmdroidBasePath = base
        configuration.osmdroidTileCache = File(base, TILE_CACHE_DIR)

        if (budgetApplied) return
        budgetApplied = true

        val maxBytes = diskCacheBudget(context)
        configuration.tileFileSystemCacheMaxBytes = maxBytes
        configuration.tileFileSystemCacheTrimBytes = maxBytes * TRIM_PERCENT / 100
        configuration.cacheMapTileOvershoot =
            if (isLowRamDevice(context)) 0 else MEMORY_TILE_OVERSHOOT.toShort()
    }

    /**
     * How many bytes of tiles may sit under `cacheDir`: [FREE_SPACE_SHARE]% of
     * what is free there, never below [MIN_DISK_CACHE_BYTES] and never above
     * [MAX_DISK_CACHE_BYTES]. A filesystem that cannot answer reports 0, which
     * lands on the floor - the same place a full phone lands, and the floor is
     * small enough to be the right answer for both.
     */
    // StorageManager#getAllocatableBytes would count space Android could free
    // by clearing other apps' caches, which is not what this budget is about:
    // the question here is how much room there is right now, and the floor
    // covers the case where the answer is none.
    @Suppress("UsableSpace")
    fun diskCacheBudget(context: Context): Long {
        val free = runCatching { context.cacheDir.usableSpace }.getOrDefault(0L)
        return (free / FREE_SPACE_SHARE)
            .coerceIn(MIN_DISK_CACHE_BYTES, MAX_DISK_CACHE_BYTES)
    }

    /**
     * Whether the phone is one Android itself calls low-RAM, which is what
     * decides how much the map may keep in memory. Shared with
     * [MapTilerMap.prepare], so there is one answer per phone.
     */
    fun isLowRamDevice(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return manager?.isLowRamDevice ?: false
    }

    private const val CACHE_DIR = "osmdroid"
    private const val TILE_CACHE_DIR = "tiles"
}
