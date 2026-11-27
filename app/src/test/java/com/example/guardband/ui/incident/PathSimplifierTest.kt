package com.example.guardband.ui.incident

import com.example.guardband.data.model.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which points of an incident's path are worth drawing. Pure: no Android, no
 * Robolectric.
 *
 * Coordinates are around Cebu City, as the rest of the project's fixtures are.
 * At this latitude one ten-thousandth of a degree of latitude is about 11 m,
 * which is what makes the distances below easy to read.
 */
class PathSimplifierTest {

    private val lat0 = 10.3157
    private val lng0 = 123.8854

    /** [north] metres north and [east] metres east of the fixture's origin. */
    private fun at(north: Double, east: Double) = Alert.Location(
        lat = lat0 + north / 111_320.0,
        lng = lng0 + east / (111_320.0 * Math.cos(Math.toRadians(lat0))),
        accuracyMeters = 8.5
    )

    @Test
    fun `a path of two points or fewer is returned untouched`() {
        val empty = emptyList<Alert.Location>()
        val one = listOf(at(0.0, 0.0))
        val two = listOf(at(0.0, 0.0), at(100.0, 0.0))

        assertSame(empty, PathSimplifier.simplify(empty))
        assertSame(one, PathSimplifier.simplify(one))
        assertSame(two, PathSimplifier.simplify(two))
    }

    @Test
    fun `points along a straight run are dropped`() {
        // 100 m of walking reported every 10 m, dead straight.
        val path = (0..10).map { at(it * 10.0, 0.0) }

        val simplified = PathSimplifier.simplify(path)

        assertEquals(listOf(path.first(), path.last()), simplified)
    }

    @Test
    fun `a corner is kept`() {
        val path = listOf(
            at(0.0, 0.0),
            at(50.0, 0.0),
            at(100.0, 0.0),
            // The turn: 100 m east of the line the run was on.
            at(100.0, 50.0),
            at(100.0, 100.0)
        )

        val simplified = PathSimplifier.simplify(path)

        assertEquals(listOf(path[0], path[2], path[4]), simplified)
    }

    @Test
    fun `jitter under the tolerance is dropped and a real detour is not`() {
        val path = listOf(
            at(0.0, 0.0),
            // 3 m off the line: inside a NEO-6M's own error, so it is noise.
            at(50.0, 3.0),
            at(100.0, 0.0),
            // 40 m off: the band really went that way.
            at(150.0, 40.0),
            at(200.0, 0.0)
        )

        val simplified = PathSimplifier.simplify(path)

        assertTrue(path[1] !in simplified)
        assertTrue(path[3] in simplified)
    }

    @Test
    fun `the first and last points always survive`() {
        val path = (0..50).map { at(it * 2.0, 0.0) }

        val simplified = PathSimplifier.simplify(path)

        assertEquals(path.first(), simplified.first())
        assertEquals(path.last(), simplified.last())
    }

    @Test
    fun `a band standing still collapses to its ends`() {
        val path = List(20) { at(0.0, 0.0) }

        val simplified = PathSimplifier.simplify(path)

        assertEquals(2, simplified.size)
    }

    @Test
    fun `order is never changed`() {
        val path = listOf(
            at(0.0, 0.0),
            at(0.0, 60.0),
            at(60.0, 60.0),
            at(60.0, 0.0),
            at(120.0, 0.0)
        )

        val simplified = PathSimplifier.simplify(path)

        assertEquals(path, simplified)
    }

    @Test
    fun `a tolerance that is not a positive distance leaves the path alone`() {
        val path = (0..10).map { at(it * 10.0, 0.0) }

        assertSame(path, PathSimplifier.simplify(path, 0.0))
        assertSame(path, PathSimplifier.simplify(path, -1.0))
        assertSame(path, PathSimplifier.simplify(path, Double.NaN))
    }

    @Test
    fun `a bigger tolerance keeps fewer points`() {
        val path = listOf(
            at(0.0, 0.0),
            at(50.0, 8.0),
            at(100.0, 0.0),
            at(150.0, 8.0),
            at(200.0, 0.0)
        )

        val tight = PathSimplifier.simplify(path, toleranceMeters = 2.0)
        val loose = PathSimplifier.simplify(path, toleranceMeters = 20.0)

        assertEquals(path.size, tight.size)
        assertEquals(2, loose.size)
    }

    @Test
    fun `a long walk is thinned a long way`() {
        // 30 minutes of the mock sender's 5 s reports, walking straight with
        // a metre or two of jitter: FR-09's span at the harness's interval.
        val path = (0..359).map { at(it * 1.5, if (it % 2 == 0) 1.0 else -1.0) }

        val simplified = PathSimplifier.simplify(path)

        assertTrue("kept ${simplified.size} of ${path.size}", simplified.size < 20)
    }
}
