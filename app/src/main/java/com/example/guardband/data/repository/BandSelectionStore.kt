package com.example.guardband.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists and broadcasts the watcher's explicit Track-tab band selection.
 *
 * The Track switcher calls [select] with the device id the user selected;
 * [WatchedBandAlertRepository] reads [selected] to override
 * [BandSelection.watchedBandOf] when the user has made a choice.
 *
 * A null selection means "use the default rule".
 */
interface BandSelectionStore {
    val selected: StateFlow<String?>
    fun select(deviceId: String?)
}

/** Factory function creating a [BandSelectionStore] backed by [SharedPreferences]. */
fun BandSelectionStore(prefs: SharedPreferences): BandSelectionStore =
    SharedPreferencesBandSelectionStore(prefs)

internal class SharedPreferencesBandSelectionStore(
    private val prefs: SharedPreferences
) : BandSelectionStore {

    private val _selected = MutableStateFlow(prefs.getString(KEY, null))

    override val selected: StateFlow<String?> = _selected.asStateFlow()

    override fun select(deviceId: String?) {
        prefs.edit().let {
            if (deviceId == null) it.remove(KEY) else it.putString(KEY, deviceId)
            it.apply()
        }
        _selected.value = deviceId
    }

    private companion object {
        const val KEY = "selected_band_id"
    }
}
