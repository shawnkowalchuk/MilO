package com.shawnkowalchuk.milo.platform.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turning a geocoder's answer into the one line a trip shows. The answers are made up in the
 * shape Google's geocoder gives them for places around Edmonton.
 */
class AddressLineTest {
    private val shopRoad =
        PlaceParts(
            houseNumber = "12",
            street = "Shop Rd",
            town = "Edmonton",
            county = "Division No. 11",
            region = "Alberta",
            firstLine = "12 Shop Rd, Edmonton, AB T5J 0A1, Canada",
            country = "Canada",
            countryCode = "CA",
            postalCode = "T5J 0A1",
        )

    // ---- The four shapes of an answer -------------------------------------------------------------

    @Test
    fun `a full street address is the house number, the street and the town`() {
        assertEquals("12 Shop Rd, Edmonton", addressLine(listOf(shopRoad)))
    }

    @Test
    fun `a street with no number is the street and the town`() {
        val highway = shopRoad.copy(houseNumber = null, street = "Yellowhead Trail NW")

        assertEquals("Yellowhead Trail NW, Edmonton", addressLine(listOf(highway)))
    }

    @Test
    fun `a place with only a town is the town`() {
        val town = PlaceParts(town = "Leduc", region = "Alberta", country = "Canada")

        assertEquals("Leduc", addressLine(listOf(town)))
    }

    @Test
    fun `nothing usable gives no line`() {
        assertNull(addressLine(emptyList()))
        assertNull(addressLine(listOf(PlaceParts())))
        // A country and a postal code alone are never shown, so they are not a line either.
        assertNull(addressLine(listOf(PlaceParts(country = "Canada", postalCode = "T5J 0A1"))))
        assertNull(addressLine(listOf(PlaceParts(street = "  ", town = ""))))
    }

    // ---- What is never shown ----------------------------------------------------------------------

    @Test
    fun `a country or a postal code is never shown`() {
        val line = addressLine(listOf(shopRoad)).orEmpty()

        assertEquals(false, line.contains("Canada"))
        assertEquals(false, line.contains("T5J"))
        assertEquals(false, line.contains("Alberta"))
    }

    @Test
    fun `a house number without a street is left out`() {
        val numberOnly = PlaceParts(houseNumber = "12", town = "Edmonton")

        assertEquals("Edmonton", addressLine(listOf(numberOnly)))
    }

    // ---- Falling back -----------------------------------------------------------------------------

    @Test
    fun `where no town is named, the district, the county or the province stands in`() {
        val rural = PlaceParts(street = "Range Rd 250", county = "Sturgeon County", region = "AB")

        assertEquals("Range Rd 250, Sturgeon County", addressLine(listOf(rural)))
        assertEquals("Range Rd 250, AB", addressLine(listOf(rural.copy(county = null))))
        assertEquals(
            "Range Rd 250, Oliver",
            addressLine(listOf(rural.copy(district = "Oliver"))),
        )
        assertEquals("Range Rd 250", addressLine(listOf(rural.copy(county = null, region = null))))
    }

    @Test
    fun `with no street and no town, the geocoder's own line is used without country and code`() {
        val onlyALine =
            PlaceParts(
                firstLine = "Elk Island Park Gate, AB T8L 0A1, Canada",
                country = "Canada",
                countryCode = "CA",
                postalCode = "T8L 0A1",
            )

        assertEquals("Elk Island Park Gate, AB", addressLine(listOf(onlyALine)))
    }

    @Test
    fun `a geocoder line that is nothing but country and code gives no line`() {
        val empty =
            PlaceParts(firstLine = "T8L 0A1, Canada", country = "Canada", postalCode = "T8L 0A1")
        val byCode = PlaceParts(firstLine = "CA", countryCode = "CA")

        assertNull(addressLine(listOf(empty)))
        assertNull(addressLine(listOf(byCode)))
    }

    // ---- Several candidates -----------------------------------------------------------------------

    @Test
    fun `of several candidates the one that says the most is used`() {
        val townOnly = PlaceParts(town = "Edmonton")
        val streetOnly = PlaceParts(street = "Shop Rd")

        assertEquals("12 Shop Rd, Edmonton", addressLine(listOf(townOnly, streetOnly, shopRoad)))
        assertEquals("Shop Rd", addressLine(listOf(townOnly, streetOnly)))
    }

    @Test
    fun `of candidates that say as much, the geocoder's first is used`() {
        val next = shopRoad.copy(houseNumber = "14")

        assertEquals("12 Shop Rd, Edmonton", addressLine(listOf(shopRoad, next)))
    }

    @Test
    fun `a house number does not put a candidate ahead of the geocoder's own order`() {
        // The geocoder lists its best match first. A numbered address further down can be the
        // building next door, or one on another street.
        val street = shopRoad.copy(houseNumber = null)

        assertEquals("Shop Rd, Edmonton", addressLine(listOf(street, shopRoad)))
        assertEquals("12 Shop Rd, Edmonton", addressLine(listOf(shopRoad, street)))
    }

    @Test
    fun `stray spaces in the geocoder's parts are closed up`() {
        val untidy = PlaceParts(houseNumber = " 12 ", street = "Shop   Rd ", town = " Edmonton")

        assertEquals("12 Shop Rd, Edmonton", addressLine(listOf(untidy)))
    }
}
