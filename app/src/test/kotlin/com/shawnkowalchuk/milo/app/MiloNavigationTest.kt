package com.shawnkowalchuk.milo.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** The back stack behind the bottom navigation bar. */
class MiloNavigationTest {
    @Test
    fun `a bottom-bar screen sits directly on top of Home`() {
        val backStack = mutableListOf("home")

        backStack.showTopLevel("trips", home = "home")

        assertEquals(listOf("home", "trips"), backStack)
    }

    @Test
    fun `switching between bottom-bar screens does not pile them up`() {
        val backStack = mutableListOf("home", "trips")

        backStack.showTopLevel("log", home = "home")

        // Back from Log leads to Home, not to Trips.
        assertEquals(listOf("home", "log"), backStack)
    }

    @Test
    fun `going to Home leaves Home alone on the stack`() {
        val backStack = mutableListOf("home", "setup", "pairing")

        backStack.showTopLevel("home", home = "home")

        assertEquals(listOf("home"), backStack)
    }

    @Test
    fun `pressing the bar's button for Setup closes the pairing screen on top of it`() {
        val backStack = mutableListOf("home", "setup", "pairing")

        backStack.showTopLevel("setup", home = "home")

        assertEquals(listOf("home", "setup"), backStack)
    }

    @Test
    fun `the pairing screen's Back arrow pressed again while it closes changes nothing more`() {
        val backStack = mutableListOf("home", "setup", "pairing")

        // The screen stays on the display while it slides away, and so does its arrow.
        repeat(3) { backStack.closeIfOnTop("pairing") }

        // Setup, where the pairing screen was opened from. Not Home, and never an empty stack.
        assertEquals(listOf("home", "setup"), backStack)
    }

    @Test
    fun `Settings opens on top of Home, and the pairing screen on top of Settings`() {
        val backStack = mutableListOf("home")

        backStack.openOnTop("settings")
        backStack.openOnTop("pairing")

        assertEquals(listOf("home", "settings", "pairing"), backStack)

        // Change truck leads to the pairing screen, and its Back arrow leads back to Settings.
        backStack.closeIfOnTop("pairing")
        assertEquals(listOf("home", "settings"), backStack)
    }

    @Test
    fun `a button pressed twice before the screen has changed opens its screen once`() {
        val backStack = mutableListOf("home")

        repeat(2) { backStack.openOnTop("settings") }

        // A back stack may hold a key only once.
        assertEquals(listOf("home", "settings"), backStack)
    }

    @Test
    fun `the bar's Home button closes Settings and whatever is on top of it`() {
        val backStack = mutableListOf("home", "settings", "pairing")

        backStack.showTopLevel("home", home = "home")

        assertEquals(listOf("home"), backStack)
    }

    @Test
    fun `Back closes one screen at a time and never the last one`() {
        val backStack = mutableListOf("home", "setup", "pairing")

        backStack.closeTop()
        assertEquals(listOf("home", "setup"), backStack)

        backStack.closeTop()
        assertEquals(listOf("home"), backStack)

        // Navigation 3 throws on an empty back stack. Back from Home is left to Android.
        backStack.closeTop()
        assertEquals(listOf("home"), backStack)
    }

    @Test
    fun `the edit screen opens on top of Trips, once, and closes back to it`() {
        val backStack = mutableListOf<Any>(HomeKey, TripsKey)

        repeat(2) { backStack.openOnTop(TripEditKey(tripId = 12)) }
        assertEquals(listOf(HomeKey, TripsKey, TripEditKey(tripId = 12)), backStack)

        // After a save, and again by the Back arrow while the screen slides away.
        repeat(2) { backStack.closeIfOnTop(TripEditKey(tripId = 12)) }
        assertEquals(listOf<Any>(HomeKey, TripsKey), backStack)
    }

    @Test
    fun `the empty edit screen, for a missed trip, is a screen of its own`() {
        val backStack = mutableListOf<Any>(HomeKey, TripsKey)

        repeat(2) { backStack.openOnTop(TripEditKey(tripId = null)) }

        assertEquals(listOf(HomeKey, TripsKey, TripEditKey(tripId = null)), backStack)
    }

    @Test
    fun `the bar keeps showing Trips while the edit screen is open`() {
        val editing = listOf(HomeKey, TripsKey, TripEditKey(tripId = 12))
        val adding = listOf(HomeKey, TripsKey, TripEditKey(tripId = null))

        assertEquals(TopLevelDestination.TRIPS, topLevelOf(editing))
        assertEquals(TopLevelDestination.TRIPS, topLevelOf(adding))
    }

    @Test
    fun `the bar's button closes the edit screen on its way to its own screen`() {
        val backStack = mutableListOf<Any>(HomeKey, TripsKey, TripEditKey(tripId = 12))

        // What the press does to the back stack. While something typed is not saved the app
        // asks first, and only then does this (UnsavedWorkTest).
        backStack.showTopLevel(LogKey, home = HomeKey)

        assertEquals(listOf<Any>(HomeKey, LogKey), backStack)
    }

    @Test
    fun `the bar shows the screen the pairing screen was opened from`() {
        assertEquals(TopLevelDestination.HOME, topLevelOf(listOf(HomeKey)))
        assertEquals(TopLevelDestination.TRIPS, topLevelOf(listOf(HomeKey, TripsKey)))
        assertEquals(TopLevelDestination.SETUP, topLevelOf(listOf(HomeKey, SetupKey, PairingKey)))
        assertEquals(TopLevelDestination.LOG, topLevelOf(listOf(HomeKey, LogKey)))
        // Settings is opened from Home, so Home stays marked, also under the pairing screen.
        assertEquals(TopLevelDestination.HOME, topLevelOf(listOf(HomeKey, SettingsKey)))
        assertEquals(
            TopLevelDestination.HOME,
            topLevelOf(listOf(HomeKey, SettingsKey, PairingKey)),
        )
    }

    @Test
    fun `the Report screen opens on top of Trips, once, for the month it is handed`() {
        val backStack = mutableListOf<NavKey>(HomeKey, TripsKey)

        repeat(2) { backStack.openOnTop(ReportKey(year = 2026, month = 9)) }
        assertEquals(listOf(HomeKey, TripsKey, ReportKey(2026, 9)), backStack)
        assertEquals(TopLevelDestination.TRIPS, topLevelOf(backStack))

        // Its own Back arrow, and again while the screen slides away.
        repeat(2) { backStack.closeIfOnTop(ReportKey(2026, 9)) }
        assertEquals(listOf<NavKey>(HomeKey, TripsKey), backStack)
    }

    @Test
    fun `Settings opened from the Report screen leads back to the report, with Trips marked`() {
        val backStack = mutableListOf<NavKey>(HomeKey, TripsKey, ReportKey(2026, 9))

        backStack.openOnTop(SettingsKey)
        assertEquals(listOf(HomeKey, TripsKey, ReportKey(2026, 9), SettingsKey), backStack)
        // The nearest screen of the bar underneath is Trips, not Home as when Settings is
        // opened with Home's cog.
        assertEquals(TopLevelDestination.TRIPS, topLevelOf(backStack))

        backStack.closeIfOnTop(SettingsKey)
        assertEquals(listOf<NavKey>(HomeKey, TripsKey, ReportKey(2026, 9)), backStack)
    }

    @Test
    fun `the bar's button closes the Report screen on its way to its own screen`() {
        val backStack = mutableListOf<Any>(HomeKey, TripsKey, ReportKey(2026, 9), SettingsKey)

        backStack.showTopLevel(HomeKey, home = HomeKey)

        assertEquals(listOf<Any>(HomeKey), backStack)
    }
}
