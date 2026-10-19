package com.example.guardband.ui.alert

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.Place
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Where alerts are looked up, and how a place reads on a row. Pure, so both
 * are JVM tests.
 */
internal object AlertPlaces {

    /**
     * Coordinates are rounded to 4 decimals (about 11 m) before they are
     * looked up. Alerts a few metres apart share one lookup and one name,
     * and the geocoder is never sent more precision than a row can use.
     */
    private const val SCALE = 10_000.0

    fun rounded(value: Double): Double = (value * SCALE).roundToLong() / SCALE

    /** The cache key for [location]: its rounded coordinates. */
    fun keyOf(location: Alert.Location): String =
        String.format(Locale.US, "%.4f,%.4f", rounded(location.lat), rounded(location.lng))

    /**
     * "Street, Area, City": the parts the place has, in that order, without
     * repeats. The province stands in only when there is no city. Null when
     * nothing usable is left, so the row falls back to coordinates.
     */
    fun label(place: Place): String? {
        val parts = listOf(
            place.street?.takeUnless { it.trim().equals(UNNAMED_ROAD, ignoreCase = true) },
            place.area,
            place.city ?: place.province
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase(Locale.ROOT) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** What Google's geocoder calls a road without a name. */
    private const val UNNAMED_ROAD = "Unnamed Road"
}

/**
 * One alert row as the adapter binds it: the alert, and its place name once
 * it is known. A row whose name is still null shows its coordinates.
 */
data class AlertRow(val alert: Alert, val placeName: String? = null)
