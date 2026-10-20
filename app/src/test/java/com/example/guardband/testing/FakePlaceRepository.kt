package com.example.guardband.testing

import com.example.guardband.data.model.Place
import com.example.guardband.data.repository.PlaceLookup
import com.example.guardband.data.repository.PlaceRepository

/**
 * [PlaceRepository] that answers from a table and records every question, so
 * a test can check what was looked up, how often and in which order.
 */
class FakePlaceRepository(
    var answer: (lat: Double, lng: Double) -> PlaceLookup = { _, _ -> PlaceLookup.Found(CEBU) }
) : PlaceRepository {

    /** Every (lat, lng) asked about, in call order. */
    val asked = mutableListOf<Pair<Double, Double>>()

    override suspend fun placeAt(lat: Double, lng: Double): PlaceLookup {
        asked += lat to lng
        return answer(lat, lng)
    }

    companion object {
        val CEBU = Place(street = "Osmeña Blvd", area = "Kamputhaw", city = "Cebu City", province = "Cebu")
        const val CEBU_LABEL = "Osmeña Blvd, Kamputhaw, Cebu City"
    }
}
