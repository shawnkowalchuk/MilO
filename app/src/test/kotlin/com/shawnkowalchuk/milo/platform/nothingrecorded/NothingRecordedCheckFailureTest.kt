package com.shawnkowalchuk.milo.platform.nothingrecorded

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An event log that cannot be written, as with a full disk. */
private object UnwritableEventLog : EventLogDao {
    override suspend fun insert(entry: EventLogEntry): Long = throw IOException("the disk is full")

    override fun observeNewest(limit: Int): Flow<List<EventLogEntry>> = flowOf(emptyList())

    override fun observeNewestOf(
        categories: List<EventCategory>,
        limit: Int,
    ): Flow<List<EventLogEntry>> = flowOf(emptyList())

    override suspend fun readAfter(afterAtMs: Long, afterId: Long, limit: Int) =
        emptyList<EventLogEntry>()

    override suspend fun count(): Int = 0

    override suspend fun atMsOfEntryBehind(newerEntries: Int): Long? = null

    override suspend fun deleteOlderThan(beforeMs: Long): Int = 0
}

/** A settings file that can be read, holds nothing, and refuses every write. */
private object ReadOnlySettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("the disk is full")
}

/**
 * A failure inside the "nothing recorded" check stays inside it. The check runs in the process
 * the trip service runs in, so an exception that got out would end a recording for the sake of
 * a notification.
 *
 * Its coroutines run in the test's background scope, and `runTest` fails a test in which one of
 * them ends with an exception. That a test here passes at all is therefore the proof that
 * nothing got out.
 */
// runCurrent() and advanceTimeBy() are how a test lets the check's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedCheckFailureTest : NothingRecordedCheckFixture() {
    @Test
    fun `a notification nobody can see is logged as unseen, and still counts as today's`() =
        runTest {
            seen = false
            val check = check()

            check.look("app opened")
            check.look("app opened")
            letItLook()

            assertEquals(1, shown)
            assertTrue(
                lines()[1].endsWith(
                    "Notifications are switched off for MilO or for this kind of notification, " +
                        "so nobody saw it.",
                ),
            )
        }

    @Test
    fun `with settings that cannot be read nothing is checked, and the log says so once`() =
        runTest {
            val check = check(UnreadableSettingsFile)

            check.arm("process start")
            repeat(3) { check.look("app opened") }
            runCurrent()

            assertEquals(0, shown)
            assertEquals(
                listOf(
                    "Nothing-recorded check: the settings cannot be read, so nothing is checked",
                ),
                lines(EventCategory.ERROR),
            )
            // The daily look goes on, as out of the box: tomorrow the file may be readable.
            assertEquals(listOf(at("2026-10-07T12:00")), alarmsAskedFor)
        }

    @Test
    fun `if the day it was shown cannot be stored, the log says that it may come again`() =
        runTest {
            val check = check(ReadOnlySettingsFile)

            check.look("app opened")
            letItLook()

            assertEquals(1, shown)
            assertTrue(lines()[1].contains("That it was shown could not be stored"))
            assertTrue(lines()[1].endsWith("so it may be shown again today."))

            check.look("app opened")
            letItLook()
            assertEquals(2, shown)
        }

    @Test
    fun `a failure while asking is written to the log and goes no further`() = runTest {
        failReadingTrips = IOException("the trips table is broken")
        val check = check()

        check.arm("process start")
        runCurrent()

        assertEquals(0, shown)
        assertEquals(
            listOf(
                "The nothing-recorded check failed while asking whether a trip has been " +
                    "recorded today (process start)",
            ),
            lines(EventCategory.ERROR),
        )
        // The alarm was asked for all the same: the two are kept apart from each other too.
        assertEquals(1, alarmsAskedFor.size)

        // And it works again once storage does.
        failReadingTrips = null
        check.look("app opened")
        letItLook()
        assertEquals(1, shown)
    }

    @Test
    fun `an alarm that Android refuses does not stop the look`() = runTest {
        failAskingForAlarm = IllegalStateException("too many alarms")
        var done = 0

        check().onAlarm { done++ }
        letItLook()

        assertEquals(
            listOf("The nothing-recorded check failed while asking for the next daily alarm"),
            lines(EventCategory.ERROR),
        )
        assertEquals(1, shown)
        assertEquals(1, done)
    }

    @Test
    fun `if the event log cannot be written either, the failure goes to a crash file`() = runTest {
        failReadingTrips = IOException("the trips table is broken")
        val crashes = crashFiles
        // It moves on, as a real clock does: a crash file is named by its time.
        clock = { nowMs++ }
        var done = 0

        check(eventLog = UnwritableEventLog).onAlarm { done++ }
        runCurrent()

        // Two pieces of work failed, each by itself: the alarm was asked for, but its line
        // could not be written, and the trips could not be read.
        val written = crashes.pendingFiles().map { crashes.read(it).summary }
        assertEquals(2, written.size)
        assertTrue(written.all { it.contains("The nothing-recorded check failed while") })
        assertEquals(0, shown)
        // The receiver is told all the same, so the broadcast is not left open.
        assertEquals(1, done)
    }

    // ---- The trip controller ----------------------------------------------------------------------

    @Test
    fun `a look waits for the trip controller, but not for ever`() = runTest {
        controllerAnswers = false
        val check = check()

        check.look("app opened")
        runCurrent()
        // Nothing yet: the controller may still be closing a trip the last process left open.
        assertEquals(0, shown)
        assertEquals(emptyList<String>(), lines())

        advanceTimeBy(CAUGHT_UP_WAIT_MS)
        runCurrent()
        // It has looked, found no trip, and waits for one that may be starting. The
        // controller is not waited for a second time: this further wait is all that is left.
        assertEquals(0, shown)
        letItLook()

        assertEquals(1, shown)
        assertTrue(
            lines().first().endsWith(
                "Asked without the trip controller having caught up: it did not answer in time.",
            ),
        )
        assertNull(log.entries.firstOrNull { it.category == EventCategory.ERROR })
    }
}
