package com.example.guardband.data.repository

import com.example.guardband.data.model.Place

/** One reverse-geocoding answer. Three outcomes, because only one of them is worth asking again. */
sealed interface PlaceLookup {
    data class Found(val place: Place) : PlaceLookup

    /** The service answered and has no name for this point. Not worth asking again. */
    object NotFound : PlaceLookup

    /** The service could not answer (offline, busy, timed out). Worth asking again later. */
    object Unavailable : PlaceLookup
}

/**
 * Turns an alert's coordinates into a place name for the alert rows.
 *
 * Read-only and best effort: a row without a name shows its coordinates, so
 * nothing here may fail loudly. Never logs a coordinate or an answer.
 */
interface PlaceRepository {
    suspend fun placeAt(lat: Double, lng: Double): PlaceLookup

    companion object {
        /** Knows no places: every row shows coordinates. The default where no Context is available. */
        val NONE: PlaceRepository = object : PlaceRepository {
            override suspend fun placeAt(lat: Double, lng: Double): PlaceLookup = PlaceLookup.NotFound
        }
    }
}
