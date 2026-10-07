package com.shawnkowalchuk.milo.platform.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a report of Android Auto's connection becomes a line of the event log, and what the
 * line says. The rule: the first report and every change after it, and only while no trip is
 * being recorded, because the trip service's own watch is written down during a trip.
 */
class AndroidAutoLogRulesTest {
    // The two values `CarConnection` gives on a phone: 2 is "projection", 0 is "not connected".
    private val projection = 2
    private val notConnected = 0

    @Test
    fun `the first report is written whatever it says, while no trip is being recorded`() {
        for (connected in listOf(true, false)) {
            val note =
                judgeAndroidAutoReport(lastKnown = null, connected, tripBeingRecorded = false)

            assertEquals(AndroidAutoNote.FIRST_READING, note)
            assertTrue(note.written)
        }
    }

    @Test
    fun `a change is written while no trip is being recorded`() {
        val connects = judgeAndroidAutoReport(lastKnown = false, connected = true, false)
        val disconnects = judgeAndroidAutoReport(lastKnown = true, connected = false, false)

        assertEquals(AndroidAutoNote.CHANGE, connects)
        assertEquals(AndroidAutoNote.CHANGE, disconnects)
        assertTrue(connects.written)
        assertTrue(disconnects.written)
    }

    @Test
    fun `nothing is written while a trip is being recorded, first report or change`() {
        for (lastKnown in listOf(null, true, false)) {
            for (connected in listOf(true, false).filter { it != lastKnown }) {
                val note = judgeAndroidAutoReport(lastKnown, connected, tripBeingRecorded = true)

                assertEquals(AndroidAutoNote.LEFT_TO_THE_TRIP_SERVICE, note)
                assertFalse(note.written)
            }
        }
    }

    @Test
    fun `a report that repeats the last one is written neither in a trip nor outside one`() {
        for (same in listOf(true, false)) {
            for (tripBeingRecorded in listOf(true, false)) {
                val note = judgeAndroidAutoReport(lastKnown = same, same, tripBeingRecorded)

                assertEquals(AndroidAutoNote.NOTHING_NEW, note)
                assertFalse(note.written)
            }
        }
    }

    @Test
    fun `a line exists exactly for what is written`() {
        for (note in AndroidAutoNote.entries) {
            for (connected in listOf(true, false)) {
                val text = androidAutoLogText(note, connected, rawType = projection)

                assertEquals("$note, connected $connected", note.written, text != null)
            }
        }
    }

    @Test
    fun `a change says which way it went, the raw value, and that no trip is being recorded`() {
        assertEquals(
            "Android Auto connected (CarConnection reports type 2). No trip is being " +
                "recorded, and Android Auto does not start one",
            androidAutoLogText(AndroidAutoNote.CHANGE, connected = true, projection),
        )
        assertEquals(
            "Android Auto disconnected (CarConnection reports type 0). No trip is being recorded",
            androidAutoLogText(AndroidAutoNote.CHANGE, connected = false, notConnected),
        )
    }

    @Test
    fun `a first reading says that it is one, and claims no change`() {
        assertEquals(
            "Android Auto is not connected (CarConnection reports type 0). First reading " +
                "since MilO's process started. No trip is being recorded",
            androidAutoLogText(AndroidAutoNote.FIRST_READING, connected = false, notConnected),
        )
        assertEquals(
            "Android Auto is connected (CarConnection reports type 2). First reading since " +
                "MilO's process started, so MilO did not see it connect. No trip is being " +
                "recorded",
            androidAutoLogText(AndroidAutoNote.FIRST_READING, connected = true, projection),
        )
    }

    @Test
    fun `a report without a raw value says so`() {
        assertEquals(
            "Android Auto disconnected (CarConnection reports no type). No trip is being " +
                "recorded",
            androidAutoLogText(AndroidAutoNote.CHANGE, connected = false, rawType = null),
        )
    }

    @Test
    fun `what is left to the trip service, or is nothing new, has no line`() {
        assertNull(androidAutoLogText(AndroidAutoNote.LEFT_TO_THE_TRIP_SERVICE, true, projection))
        assertNull(androidAutoLogText(AndroidAutoNote.NOTHING_NEW, false, notConnected))
    }
}
