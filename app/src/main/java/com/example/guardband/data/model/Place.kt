package com.example.guardband.data.model

/**
 * What a reverse geocoder knows about a point, as separate parts so the UI
 * decides how much of it to show. Any part may be missing.
 *
 * @param street   the road, e.g. "Osmeña Blvd".
 * @param area     the neighbourhood or barangay, e.g. "Kamputhaw".
 * @param city     the city or municipality, e.g. "Cebu City".
 * @param province the province or region, used only when there is no city.
 */
data class Place(
    val street: String? = null,
    val area: String? = null,
    val city: String? = null,
    val province: String? = null
)
