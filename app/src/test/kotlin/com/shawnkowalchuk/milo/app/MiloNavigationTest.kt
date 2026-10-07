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
    fun `pressing the bar's button for Settings closes what was opened on top of it`() {
        val backStack = mutableListOf("home", "settings", "setup", "pairing")

        backStack.showTopLevel("settings", home = "home")

        assertEquals(listOf("home", "settings"), backStack)
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
    fun `Setup opens on top of Settings, and the pairing screen on top of Setup`() {
        val backStack = mutableListOf<NavKey>(HomeKey)

        backStack.showTopLevel(SettingsKey, home = HomeKey)
        backStack.openOnTop(SetupKey)
        backStack.openOnTop(PairingKey)

        assertEquals(listOf(HomeKey, SettingsKey, SetupKey, PairingKey), backStack)

        // Each name at the top leads back one screen: to Setup, then to Settings.
        backStack.closeIfOnTop(PairingKey)
        assertEquals(listOf<NavKey>(HomeKey, SettingsKey, SetupKey), backStack)
        backStack.closeIfOnTop(SetupKey)
        assertEquals(listOf<NavKey>(HomeKey, SettingsKey), backStack)
    }

    @Test
    fun `Home's setup warning opens Setup on top of Home, and Back leads to Home`() {
        val backStack = mutableListOf<NavKey>(HomeKey)

        backStack.openOnTop(SetupKey)
        assertEquals(listOf<NavKey>(HomeKey, SetupKey), backStack)
        assertEquals(TopLevelDestination.HOME, topLevelOf(backStack))

        backStack.closeIfOnTop(SetupKey)
        assertEquals(listOf<NavKey>(HomeKey), backStack)
    }

    @Test
    fun `a button pressed twice before the screen has changed opens its screen once`() {
        val backStack = mutableListOf("home")

        repeat(2) { backStack.openOnTop("setup") }

        // A back stack may hold a key only once.
        assertEquals(listOf("home", "setup"), backStack)
    }

    @Test
    fun `the bar's Home button closes Settings and whatever is on top of it`() {
        val backStack = mutableListOf("home", "settings", "setup", "pairing")

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
        assertEquals(TopLevelDestination.SETTINGS, topLevelOf(listOf(HomeKey, SettingsKey)))
        assertEquals(TopLevelDestination.LOG, topLevelOf(listOf(HomeKey, LogKey)))
        // Setup and the pairing screen are opened from Settings, which stays marked under them.
        assertEquals(
            TopLevelDestination.SETTINGS,
            topLevelOf(listOf(HomeKey, SettingsKey, SetupKey, PairingKey)),
        )
        assertEquals(
            TopLevelDestination.SETTINGS,
            topLevelOf(listOf(HomeKey, SettingsKey, PairingKey)),
        )
        // Opened from Home's own tiles, they keep Home marked.
        assertEquals(TopLevelDestination.HOME, topLevelOf(listOf(HomeKey, SetupKey, PairingKey)))
        assertEquals(TopLevelDestination.HOME, topLevelOf(listOf(HomeKey, PairingKey)))
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
        // Settings is a screen of the bar, but here it was opened on top of the report: the bar
        // keeps showing Trips, where the report was opened from.
        assertEquals(TopLevelDestination.TRIPS, topLevelOf(backStack))

        backStack.closeIfOnTop(SettingsKey)
        assertEquals(listOf<NavKey>(HomeKey, TripsKey, ReportKey(2026, 9)), backStack)
    }

    @Test
    fun `a tap on the monthly reminder shows that month's report as if opened from Trips`() {
        val report = ReportKey(year = 2026, month = 9)
        val wherever =
            listOf(
                listOf<NavKey>(HomeKey),
                listOf(HomeKey, LogKey),
                listOf(HomeKey, SettingsKey, SetupKey, PairingKey),
                listOf(HomeKey, TripsKey, TripEditKey(tripId = 12)),
                // Another month's report was open.
                listOf(HomeKey, TripsKey, ReportKey(2026, 8), SettingsKey),
            )

        for (open in wherever) {
            val backStack = open.toMutableList()

            backStack.showReport(report)

            // Back leads to Trips and then to Home, and the bar marks Trips.
            assertEquals(listOf(HomeKey, TripsKey, report), backStack)
            assertEquals(TopLevelDestination.TRIPS, topLevelOf(backStack))
        }
    }

    @Test
    fun `a reminder tapped while that very report is open leaves one report on the stack`() {
        val report = ReportKey(year = 2026, month = 9)
        val backStack = mutableListOf<NavKey>(HomeKey, TripsKey, report)

        backStack.showReport(report)

        assertEquals(listOf(HomeKey, TripsKey, report), backStack)
    }

    @Test
    fun `the bar's button closes the Report screen on its way to its own screen`() {
        val backStack = mutableListOf<Any>(HomeKey, TripsKey, ReportKey(2026, 9), SettingsKey)

        backStack.showTopLevel(HomeKey, home = HomeKey)

        assertEquals(listOf<Any>(HomeKey), backStack)
    }
}
