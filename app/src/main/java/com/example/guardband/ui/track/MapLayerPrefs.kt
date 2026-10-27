package com.example.guardband.ui.track

import android.content.Context
import android.content.SharedPreferences

/**
 * The one thing the Track map remembers on this phone: the [MapLayer] picked
 * in the layer picker, so the map reopens on it instead of on [MapLayer.AUTO].
 * Per phone, not per account, like the display setting. Only the style's
 * name is stored, never a position.
 */
interface MapLayerPrefs {
    var layer: MapLayer

    companion object {
        /** The live store. `RepositoryProvider` holds no Context, so callers that have one build it. */
        fun from(context: Context): MapLayerPrefs =
            SharedPreferencesMapLayerPrefs(
                context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            )

        /** What a fresh install, or an unreadable stored value, starts on. */
        val DEFAULT = MapLayer.AUTO

        /**
         * The stored name back as a layer. A name this build does not know (a
         * layer since removed or renamed) falls back to [DEFAULT] rather than
         * crashing the map.
         */
        fun fromStored(name: String?): MapLayer =
            MapLayer.entries.find { it.name == name } ?: DEFAULT

        private const val FILE_NAME = "map"
    }
}

class SharedPreferencesMapLayerPrefs(
    private val prefs: SharedPreferences
) : MapLayerPrefs {

    override var layer: MapLayer
        get() = MapLayerPrefs.fromStored(prefs.getString(KEY_LAYER, null))
        set(value) = prefs.edit().putString(KEY_LAYER, value.name).apply()

    private companion object {
        const val KEY_LAYER = "layer"
    }
}
