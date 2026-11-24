package com.example.guardband.ui.incident

import com.example.guardband.data.model.Alert
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Thins an incident's tracking path down to the points that give it its shape,
 * by Ramer-Douglas-Peucker. Pure, so every rule below is a JVM test.
 *
 * **Why it is needed.** FR-09's real band sends one tracking update a minute
 * for half an hour: 30 points, which no map minds. The mock sender stands in
 * for it at one every 5 s, and [com.example.guardband.data.repository.AlertRepository.INCIDENT_WINDOW]
 * reads up to 400 entries, so the Incident view's polyline can carry a few
 * hundred points - almost all of them a band standing still or walking
 * straight, each one re-projected to pixels on every single frame of a pan.
 *
 * **What it keeps.** The first and last point always, and every point further
 * than [toleranceMeters] from the straight line between the points that
 * survive around it. So a corner is kept; a point that only says "still
 * walking the same way" is dropped. Nothing is reordered and nothing is moved.
 *
 * **Why [DEFAULT_TOLERANCE_METERS] is 5 m.** It is below the position error
 * the band's own GPS has (the NEO-6M is about 2.5 m in open sky and 5-10 m
 * between buildings), so what it removes is jitter along a run the band
 * really did walk in a straight line, never a turn it really made. A guardian
 * reading the route sees the same route.
 *
 * **It is for drawing only.** The incident's own data - the tracking updates,
 * their count, the newest one, the end marker, the fitted bounding box - all
 * still come from [Incident], untouched. Nothing here is written anywhere.
 *
 * Distances are measured on a local flat projection around each segment
 * rather than on the sphere: over the few hundred metres an incident spans
 * the difference is far under a millimetre, and it keeps this a plain JVM
 * test, as [com.example.guardband.ui.track.AlertPinSelector] does for the
 * same reason.
 *
 * Nothing here logs: every input is a location.
 */
object PathSimplifier {

    /** Metres per degree of latitude; longitude is this times cos(latitude). */
    private const val METERS_PER_DEGREE = 111_320.0

    /** How far off the line a point must be to be worth keeping. See the KDoc. */
    const val DEFAULT_TOLERANCE_METERS = 5.0

    /**
     * [path] with its redundant points removed.
     *
     * @param path the points to draw, in order. Returned as given when there
     *   is nothing to remove (two points or fewer) or [toleranceMeters] is not
     *   a positive, finite distance.
     */
    fun simplify(
        path: List<Alert.Location>,
        toleranceMeters: Double = DEFAULT_TOLERANCE_METERS
    ): List<Alert.Location> {
        if (path.size <= 2) return path
        if (!toleranceMeters.isFinite() || toleranceMeters <= 0.0) return path

        val keep = BooleanArray(path.size)
        keep[0] = true
        keep[path.size - 1] = true

        // An explicit stack rather than recursion: a long walk is hundreds of
        // points deep and a path of them is not worth a stack frame each.
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.addLast(0 to path.size - 1)

        while (pending.isNotEmpty()) {
            val (first, last) = pending.removeLast()
            if (last - first < 2) continue

            var farthest = -1
            var farthestDistance = 0.0
            for (index in first + 1 until last) {
                val distance = distanceToSegment(path[index], path[first], path[last])
                if (distance > farthestDistance) {
                    farthestDistance = distance
                    farthest = index
                }
            }

            // Everything between these two is within the tolerance of the line
            // they draw, so the line already says what they said.
            if (farthest < 0 || farthestDistance <= toleranceMeters) continue

            keep[farthest] = true
            pending.addLast(first to farthest)
            pending.addLast(farthest to last)
        }

        return path.filterIndexed { index, _ -> keep[index] }
    }

    /**
     * How far [point] is, in metres, from the segment [start]-[end]. A segment
     * whose ends coincide degrades to the distance between two points, which
     * is what a band that has not moved produces.
     */
    private fun distanceToSegment(
        point: Alert.Location,
        start: Alert.Location,
        end: Alert.Location
    ): Double {
        // Metres east and north of `start`, at `start`'s latitude: over an
        // incident's span the scale does not change enough to matter.
        val scaleX = METERS_PER_DEGREE * cos(Math.toRadians(start.lat))
        val px = (point.lng - start.lng) * scaleX
        val py = (point.lat - start.lat) * METERS_PER_DEGREE
        val ex = (end.lng - start.lng) * scaleX
        val ey = (end.lat - start.lat) * METERS_PER_DEGREE

        val lengthSquared = ex * ex + ey * ey
        if (lengthSquared == 0.0) return sqrt(px * px + py * py)

        // Where along the segment the perpendicular lands, clamped to its ends.
        val along = ((px * ex + py * ey) / lengthSquared).coerceIn(0.0, 1.0)
        val dx = px - along * ex
        val dy = py - along * ey
        return sqrt(dx * dx + dy * dy)
    }
}
