package com.example.guardband.testing

import com.example.guardband.ui.track.MapLayer
import com.example.guardband.ui.track.MapLayerPrefs

/** In-memory [MapLayerPrefs], counting writes. */
class FakeMapLayerPrefs(
    initial: MapLayer = MapLayerPrefs.DEFAULT
) : MapLayerPrefs {

    var writes = 0
        private set

    override var layer: MapLayer = initial
        set(value) {
            field = value
            writes++
        }
}
