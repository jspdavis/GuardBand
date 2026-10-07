package com.example.guardband.ui.track

/**
 * The maps link the Navigate button opens (D6).
 *
 * Google Maps' documented universal URL, which needs no API key and no
 * billing, and which any installed maps app can claim through an
 * `ACTION_VIEW`. Pure, so it is unit-testable.
 */
internal object NavigationUrl {

    /**
     * A maps search centred on the band's last known position.
     *
     * Built by string interpolation rather than `String.format`, deliberately:
     * `%f` is locale-aware, so on a device set to a locale that uses a decimal
     * comma it would emit `query=10,3157,123,8854` and the link would break.
     * `Double.toString` always uses a dot.
     */
    fun googleMapsSearch(lat: Double, lng: Double): String =
        "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
}
