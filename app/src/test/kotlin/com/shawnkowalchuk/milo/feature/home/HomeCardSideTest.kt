package com.shawnkowalchuk.milo.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which side of today's card is up while a trip is recorded (Shawn, 2026-10-10: "when you
 * click it it toggles between days and month"), and that each new trip starts with Today.
 */
class HomeCardSideTest {
    @Test
    fun `the card starts with Today`() {
        assertEquals(CardSide.TODAY, cardSide(monthChosenIn = null, tripId = 7))
    }

    @Test
    fun `a press turns it to the month, and the next press back to Today`() {
        val once = afterPress(monthChosenIn = null, tripId = 7)
        assertEquals(CardSide.MONTH, cardSide(once, tripId = 7))

        val twice = afterPress(once, tripId = 7)
        assertNull(twice)
        assertEquals(CardSide.TODAY, cardSide(twice, tripId = 7))

        val thrice = afterPress(twice, tripId = 7)
        assertEquals(CardSide.MONTH, cardSide(thrice, tripId = 7))
    }

    @Test
    fun `the month stays up for as long as the trip it was asked for in`() {
        val turned = afterPress(monthChosenIn = null, tripId = 7)

        assertEquals(CardSide.MONTH, cardSide(turned, tripId = 7))
        assertEquals(CardSide.MONTH, cardSide(turned, tripId = 7))
    }

    @Test
    fun `a new trip starts with Today again`() {
        val turnedInTheLastTrip = afterPress(monthChosenIn = null, tripId = 7)

        assertEquals(CardSide.TODAY, cardSide(turnedInTheLastTrip, tripId = 8))
    }

    @Test
    fun `the first press in a new trip turns the card to the month, whatever the last trip left`() {
        val turnedInTheLastTrip = afterPress(monthChosenIn = null, tripId = 7)

        val pressed = afterPress(turnedInTheLastTrip, tripId = 8)

        assertEquals(CardSide.MONTH, cardSide(pressed, tripId = 8))
        // And it is no longer up for the trip before, should that one ever be asked about.
        assertEquals(CardSide.TODAY, cardSide(pressed, tripId = 7))
    }
}
