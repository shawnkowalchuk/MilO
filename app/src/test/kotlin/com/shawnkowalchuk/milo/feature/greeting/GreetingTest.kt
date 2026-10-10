package com.shawnkowalchuk.milo.feature.greeting

import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the mascot walks in over Home, and when a start goes without its greeting. */
class GreetingTest {
    private fun greetingOn(
        asked: Boolean = true,
        firstRun: FirstRunStage? = FirstRunStage.DONE,
        onHome: Boolean = true,
        notificationTapped: Boolean = false,
    ) = greeting(asked, firstRun, onHome, notificationTapped)

    @Test
    fun `a fresh start on Home with nothing else going on is greeted`() {
        assertEquals(Greeting.SHOWING, greetingOn())
    }

    @Test
    fun `a start that is not fresh, or has had its greeting, gets none`() {
        assertEquals(Greeting.NONE, greetingOn(asked = false))
        // Whatever else is true.
        assertEquals(Greeting.NONE, greetingOn(asked = false, firstRun = null))
        assertEquals(Greeting.NONE, greetingOn(asked = false, onHome = false))
    }

    @Test
    fun `it waits while the settings are being read, and drops nothing yet`() {
        assertEquals(Greeting.WAITING, greetingOn(firstRun = null))
        assertEquals(Greeting.WAITING, greetingOn(firstRun = null, onHome = false))
        assertEquals(Greeting.WAITING, greetingOn(firstRun = null, notificationTapped = true))
    }

    @Test
    fun `the first start is not greeted, on its first page or on Setup`() {
        assertEquals(Greeting.DROPPED, greetingOn(firstRun = FirstRunStage.INTRO))
        assertEquals(Greeting.DROPPED, greetingOn(firstRun = FirstRunStage.SETUP))
    }

    @Test
    fun `another screen on top, such as What's new after an update, drops it`() {
        assertEquals(Greeting.DROPPED, greetingOn(onHome = false))
    }

    @Test
    fun `a tap on a notification drops it, even one that leads to Home`() {
        assertEquals(Greeting.DROPPED, greetingOn(notificationTapped = true))
    }
}
