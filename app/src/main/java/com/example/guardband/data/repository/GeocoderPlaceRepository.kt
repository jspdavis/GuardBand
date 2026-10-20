package com.example.guardband.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.guardband.data.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * [PlaceRepository] on Android's own [Geocoder]: free, no key, no dependency
 * and **no permission** (it geocodes the coordinates it is given; it never
 * reads the phone's location).
 *
 * On phones with Google Play services the answers come from Google's
 * geocoding service, which therefore receives the coordinates it is asked
 * about, and the Privacy Notice says so. A phone without a geocoder
 * ([Geocoder.isPresent] false) gets [PlaceLookup.NotFound] for everything,
 * and its rows keep their coordinates.
 *
 * Holds the application context only. Nothing here logs.
 */
class GeocoderPlaceRepository(context: Context) : PlaceRepository {

    private val appContext = context.applicationContext

    override suspend fun placeAt(lat: Double, lng: Double): PlaceLookup {
        if (!Geocoder.isPresent()) return PlaceLookup.NotFound
        val geocoder = Geocoder(appContext, Locale.getDefault())

        val addresses = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocodeAsync(geocoder, lat, lng)
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)
                    }
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            // Out-of-range coordinates: no name will ever exist for them.
            return PlaceLookup.NotFound
        } ?: return PlaceLookup.Unavailable

        val address = addresses.firstOrNull() ?: return PlaceLookup.NotFound
        return PlaceLookup.Found(placeOf(address))
    }

    /** The API 33+ form, which does not block a thread. Null means it reported an error. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(geocoder: Geocoder, lat: Double, lng: Double): List<Address>? =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(addresses)
                }

                // Called from API 34; on 33 an error never calls back, which the timeout covers.
                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }

    private fun placeOf(address: Address) = Place(
        street = address.thoroughfare,
        area = address.subLocality,
        city = address.locality ?: address.subAdminArea,
        province = address.adminArea
    )

    private companion object {
        /** Long enough for a slow connection; a row is never left waiting on a dead one. */
        const val TIMEOUT_MS = 10_000L
    }
}
