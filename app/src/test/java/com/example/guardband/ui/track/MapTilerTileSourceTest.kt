package com.example.guardband.ui.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The map's two styles: light Streets in light mode, Streets Dark in dark
 * mode, each under its own cache name so a theme switch never shows the
 * other's tiles.
 */
@RunWith(RobolectricTestRunner::class)
class MapTilerTileSourceTest {

    @Test
    fun `light mode gets Streets and dark mode Streets Dark`() {
        assertEquals(MapTilerTileSource.STREETS, MapTilerTileSource.forNightMode(false))
        assertEquals(MapTilerTileSource.STREETS_DARK, MapTilerTileSource.forNightMode(true))
    }

    @Test
    fun `the two styles are cached apart, and the light one keeps its old cache name`() {
        assertEquals("MapTilerStreets", MapTilerTileSource.STREETS.name())
        assertEquals("MapTilerStreetsDark", MapTilerTileSource.STREETS_DARK.name())
        assertNotEquals(MapTilerTileSource.STREETS.name(), MapTilerTileSource.STREETS_DARK.name())
    }

    @Test
    fun `both styles keep MapTiler's zoom range`() {
        for (source in listOf(MapTilerTileSource.STREETS, MapTilerTileSource.STREETS_DARK)) {
            assertEquals(0, source.minimumZoomLevel)
            assertEquals(22, source.maximumZoomLevel)
        }
    }
}
