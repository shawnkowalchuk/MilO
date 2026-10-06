package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.saveable.SaverScope
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The question before a screen is left with something typed and not saved: which ways out ask,
 * and where each leads once it is answered.
 */
class UnsavedWorkTest {
    private val holding = UnsavedWork().reported(held = true)

    @Test
    fun `with nothing typed every way out is taken at once`() {
        assertNull(UnsavedWork().asked(to = null))
        for (destination in TopLevelDestination.entries) {
            assertNull("$destination", UnsavedWork().asked(destination))
        }
    }

    @Test
    fun `with something typed Back asks, and so does every button of the bottom bar`() {
        assertEquals(
            UnsavedWork(unsaved = true, asking = true, askedFor = null),
            holding.asked(to = null),
        )
        for (destination in TopLevelDestination.entries) {
            assertEquals(
                "$destination",
                UnsavedWork(unsaved = true, asking = true, askedFor = destination),
                holding.asked(destination),
            )
        }
    }

    @Test
    fun `Keep editing closes the question and asks again at the next way out`() {
        val kept = checkNotNull(holding.asked(TopLevelDestination.LOG)).kept()

        assertEquals(holding, kept)
        assertFalse(kept.asking)
        assertTrue(checkNotNull(kept.asked(to = null)).asking)
    }

    @Test
    fun `a screen that holds nothing any more closes the question that was open`() {
        // The form was saved, or put back, while the question stood there.
        val asking = checkNotNull(holding.asked(to = null))

        assertEquals(UnsavedWork(), asking.reported(held = false))
        assertEquals(asking, asking.reported(held = true))
    }

    @Test
    fun `it is saved and read back as it was`() {
        val states =
            listOf(
                UnsavedWork(),
                holding,
                checkNotNull(holding.asked(to = null)),
                checkNotNull(holding.asked(TopLevelDestination.SETUP)),
            )

        // Android keeps plain values only; this scope accepts what a Bundle would.
        val plainValuesOnly = SaverScope { it is Boolean || it is String }
        for (state in states) {
            val saved = with(UnsavedWorkSaver) { plainValuesOnly.save(state) }

            assertEquals(state, UnsavedWorkSaver.restore(checkNotNull(saved)))
        }
    }

    // ---- Where the way out leads, once Discard is pressed ---------------------------------------

    private val editing = listOf(HomeKey, TripsKey, TripEditKey(tripId = 12))

    @Test
    fun `Back leads from the edit screen to Trips`() {
        val backStack = editing.toMutableList()

        backStack.leaveTop(to = null)

        assertEquals(listOf<NavKey>(HomeKey, TripsKey), backStack)
    }

    @Test
    fun `a button of the bottom bar leads to its screen, with the edit screen closed`() {
        val toLog = editing.toMutableList()
        val toHome = editing.toMutableList()
        val toTrips = editing.toMutableList()

        toLog.leaveTop(TopLevelDestination.LOG)
        toHome.leaveTop(TopLevelDestination.HOME)
        toTrips.leaveTop(TopLevelDestination.TRIPS)

        assertEquals(listOf<NavKey>(HomeKey, LogKey), toLog)
        assertEquals(listOf<NavKey>(HomeKey), toHome)
        assertEquals(listOf<NavKey>(HomeKey, TripsKey), toTrips)
    }
}
