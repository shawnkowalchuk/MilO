package com.shawnkowalchuk.milo.app

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
    fun `the bar shows the screen the pairing screen was opened from`() {
        assertEquals(TopLevelDestination.HOME, topLevelOf(listOf(HomeKey)))
        assertEquals(TopLevelDestination.TRIPS, topLevelOf(listOf(HomeKey, TripsKey)))
        assertEquals(TopLevelDestination.SETUP, topLevelOf(listOf(HomeKey, SetupKey, PairingKey)))
        assertEquals(TopLevelDestination.LOG, topLevelOf(listOf(HomeKey, LogKey)))
    }
}
