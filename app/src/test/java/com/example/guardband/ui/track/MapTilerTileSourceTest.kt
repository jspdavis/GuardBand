package com.example.guardband.ui.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    // ── The extra layers (Satellite, Outdoor, Topo) ────────────────────────────

    @Test
    fun `each extra layer has its own cache name, distinct from Streets and each other`() {
        assertEquals("MapTilerSatellite", MapTilerTileSource.SATELLITE.name())
        assertEquals("MapTilerOutdoor", MapTilerTileSource.OUTDOOR.name())
        assertEquals("MapTilerTopo", MapTilerTileSource.TOPO.name())

        val names = listOf(
            MapTilerTileSource.STREETS,
            MapTilerTileSource.STREETS_DARK,
            MapTilerTileSource.SATELLITE,
            MapTilerTileSource.OUTDOOR,
            MapTilerTileSource.TOPO,
        ).map { it.name() }

        // A new name starts on clean cache rows rather than re-serving another
        // style's tiles, so every name must be unique.
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `the extra layers keep MapTiler's zoom range`() {
        for (source in listOf(
            MapTilerTileSource.SATELLITE,
            MapTilerTileSource.OUTDOOR,
            MapTilerTileSource.TOPO,
        )) {
            assertEquals(0, source.minimumZoomLevel)
            assertEquals(22, source.maximumZoomLevel)
        }
    }

    @Test
    fun `forLayer resolves Auto to theme variant, Street to light Streets, and others to themselves`() {
        assertEquals(MapTilerTileSource.STREETS, MapTilerTileSource.forLayer(MapLayer.AUTO, night = false))
        assertEquals(MapTilerTileSource.STREETS_DARK, MapTilerTileSource.forLayer(MapLayer.AUTO, night = true))

        // Street is always light Streets
        assertEquals(MapTilerTileSource.STREETS, MapTilerTileSource.forLayer(MapLayer.STREET, night = false))
        assertEquals(MapTilerTileSource.STREETS, MapTilerTileSource.forLayer(MapLayer.STREET, night = true))

        // Satellite is the same in either mode
        assertEquals(MapTilerTileSource.SATELLITE, MapTilerTileSource.forLayer(MapLayer.SATELLITE, night = false))
        assertEquals(MapTilerTileSource.SATELLITE, MapTilerTileSource.forLayer(MapLayer.SATELLITE, night = true))

        // Outdoor is the same in either mode
        assertEquals(MapTilerTileSource.OUTDOOR, MapTilerTileSource.forLayer(MapLayer.OUTDOOR, night = false))
        assertEquals(MapTilerTileSource.OUTDOOR, MapTilerTileSource.forLayer(MapLayer.OUTDOOR, night = true))

        // Topo is the same in either mode
        assertEquals(MapTilerTileSource.TOPO, MapTilerTileSource.forLayer(MapLayer.TOPO, night = false))
        assertEquals(MapTilerTileSource.TOPO, MapTilerTileSource.forLayer(MapLayer.TOPO, night = true))
    }

    @Test
    fun `forLayer covers every selectable layer`() {
        // A guard against adding a MapLayer the resolver forgets.
        MapLayer.entries.forEach { layer ->
            MapTilerTileSource.forLayer(layer, night = false)
            MapTilerTileSource.forLayer(layer, night = true)
        }
    }

    @Test
    fun `the logo follows the tiles, not the theme`() {
        // White-lettered logo on these; the dark-lettered one on the rest.
        assertTrue(MapTilerTileSource.STREETS_DARK.drawsDark)
        assertTrue(MapTilerTileSource.SATELLITE.drawsDark)
        assertFalse(MapTilerTileSource.STREETS.drawsDark)
        assertFalse(MapTilerTileSource.OUTDOOR.drawsDark)
        assertFalse(MapTilerTileSource.TOPO.drawsDark)

        // Street in dark mode is still light; Satellite in light mode is still dark; Outdoor and Topo are light.
        assertFalse(MapTilerTileSource.forLayer(MapLayer.STREET, night = true).drawsDark)
        assertTrue(MapTilerTileSource.forLayer(MapLayer.SATELLITE, night = false).drawsDark)
        assertFalse(MapTilerTileSource.forLayer(MapLayer.OUTDOOR, night = true).drawsDark)
        assertFalse(MapTilerTileSource.forLayer(MapLayer.TOPO, night = true).drawsDark)
    }
}
