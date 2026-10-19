package com.example.guardband.ui.alert

import com.example.guardband.data.model.Alert
import com.example.guardband.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How a place reads on an alert row, and which points share a lookup. */
class AlertPlacesTest {

    @Test
    fun `a full place reads street, area, city`() {
        assertEquals(
            "Osmeña Blvd, Kamputhaw, Cebu City",
            AlertPlaces.label(Place("Osmeña Blvd", "Kamputhaw", "Cebu City", "Cebu"))
        )
    }

    @Test
    fun `missing parts are skipped`() {
        assertEquals("Kamputhaw, Cebu City", AlertPlaces.label(Place(area = "Kamputhaw", city = "Cebu City")))
        assertEquals("Osmeña Blvd, Cebu City", AlertPlaces.label(Place(street = "Osmeña Blvd", city = "Cebu City")))
    }

    @Test
    fun `the province stands in only when there is no city`() {
        assertEquals("Lahug, Cebu", AlertPlaces.label(Place(area = "Lahug", province = "Cebu")))
        assertEquals("Lahug, Cebu City", AlertPlaces.label(Place(area = "Lahug", city = "Cebu City", province = "Cebu")))
    }

    @Test
    fun `an unnamed road is not a street name`() {
        assertEquals("Busay, Cebu City", AlertPlaces.label(Place("Unnamed Road", "Busay", "Cebu City")))
    }

    @Test
    fun `a part repeated in another is shown once`() {
        assertEquals("Mandaue City", AlertPlaces.label(Place(area = "Mandaue City", city = "mandaue city")))
    }

    @Test
    fun `blank parts are skipped and the rest trimmed`() {
        assertEquals("Lahug, Cebu City", AlertPlaces.label(Place(street = "  ", area = " Lahug ", city = "Cebu City")))
    }

    @Test
    fun `nothing usable is null, so the row keeps its coordinates`() {
        assertNull(AlertPlaces.label(Place()))
        assertNull(AlertPlaces.label(Place(street = "Unnamed Road", area = " ")))
    }

    @Test
    fun `points about a metre apart share one key`() {
        val a = Alert.Location(lat = 10.315701, lng = 123.885402)
        val b = Alert.Location(lat = 10.315698, lng = 123.885398)

        assertEquals(AlertPlaces.keyOf(a), AlertPlaces.keyOf(b))
        assertEquals("10.3157,123.8854", AlertPlaces.keyOf(a))
    }

    @Test
    fun `points tens of metres apart get their own keys`() {
        val a = Alert.Location(lat = 10.3157, lng = 123.8854)
        val b = Alert.Location(lat = 10.3161, lng = 123.8854)

        assertEquals(false, AlertPlaces.keyOf(a) == AlertPlaces.keyOf(b))
    }

    @Test
    fun `rounding keeps four decimals`() {
        assertEquals(10.3157, AlertPlaces.rounded(10.31574), 0.0)
        assertEquals(10.3158, AlertPlaces.rounded(10.31576), 0.0)
        assertEquals(-33.8688, AlertPlaces.rounded(-33.86882), 0.0)
    }
}
