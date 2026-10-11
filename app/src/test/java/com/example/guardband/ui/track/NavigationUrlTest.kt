package com.example.guardband.ui.track

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationUrlTest {

    @Test
    fun `it builds Google Maps' documented keyless search URL`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=10.3157,123.8854",
            NavigationUrl.googleMapsSearch(10.3157, 123.8854)
        )
    }

    @Test
    fun `negative coordinates keep their sign`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=-33.8688,-151.2093",
            NavigationUrl.googleMapsSearch(-33.8688, -151.2093)
        )
    }

    @Test
    fun `the decimal separator is a dot even in a comma locale`() {
        // The reason the builder interpolates instead of using String.format:
        // "%f" is locale-aware, and query=10,3157,123,8854 is a broken link.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            assertEquals(
                "https://www.google.com/maps/search/?api=1&query=10.3157,123.8854",
                NavigationUrl.googleMapsSearch(10.3157, 123.8854)
            )
        } finally {
            Locale.setDefault(original)
        }
    }
}
