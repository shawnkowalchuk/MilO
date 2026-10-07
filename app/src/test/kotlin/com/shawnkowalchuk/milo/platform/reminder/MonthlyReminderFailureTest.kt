package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

/**
 * A failure inside the monthly reminder stays inside it. The reminder runs in the process the
 * trip service runs in, so an exception that got out would end a recording for the sake of a
 * reminder.
 *
 * Its coroutines run in the test's background scope, and `runTest` fails a test in which one of
 * them ends with an exception. That a test here passes at all is therefore the proof that
 * nothing got out.
 */
// runCurrent() is how a test lets the reminder's coroutines run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReminderFailureTest : MonthlyReminderFixture() {
    @Test
    fun `a reminder nobody can see is logged as unseen, and still counts as today's`() = runTest {
        seen = false
        val reminder = reminder()

        reminder.look("app opened")
        reminder.look("app opened")
        runCurrent()

        assertEquals(listOf(september), shownFor)
        assertTrue(
            lines().single().endsWith(
                "Notifications are switched off for MilO or for the monthly reminder, so " +
                    "nobody saw it.",
            ),
        )
    }

    @Test
    fun `with settings that cannot be read nothing is shown, and the log says so once`() = runTest {
        val reminder = reminder(UnreadableSettingsFile)

        repeat(3) { reminder.look("app opened") }
        runCurrent()

        assertEquals(emptyList<YearMonth>(), shownFor)
        assertEquals(
            listOf("Monthly reminder: the settings cannot be read, so nothing is shown"),
            lines(EventCategory.ERROR),
        )
    }

    @Test
    fun `a failure while looking is written to the log and goes no further`() = runTest {
        failReadingTrips = IOException("the trips table is broken")
        val reminder = reminder()

        reminder.arm("process start")
        runCurrent()

        assertEquals(emptyList<YearMonth>(), shownFor)
        assertEquals(
            listOf(
                "The monthly reminder failed while looking at whether the reminder is due " +
                    "(process start)",
            ),
            lines(EventCategory.ERROR),
        )
        // The alarm was asked for all the same: the two are kept apart from each other too.
        assertEquals(1, alarmsAskedFor.size)

        // And it works again once storage does.
        failReadingTrips = null
        reminder.look("app opened")
        runCurrent()
        assertEquals(listOf(september), shownFor)
    }

    @Test
    fun `if the event log cannot be written either, the failure goes to a crash file`() = runTest {
        failReadingTrips = IOException("the trips table is broken")
        val crashes = crashFiles
        val brokenLog =
            MonthlyReminder(
                alarm = object : ReminderAlarm {
                    override fun setFor(atMs: Long) = throw IllegalStateException("too many alarms")
                },
                show = { true },
                withdraw = {},
                settings = settings,
                sentReports = { sent },
                tripsStartedBetween = { _, _ -> throw IOException("the trips table is broken") },
                eventLog = EventLogRepository(UnwritableEventLog),
                crashFileStore = crashes,
                // It moves on, as a real clock does: a crash file is named by its time.
                clock = { nowMs++ },
                zone = { zone },
                scope = backgroundScope,
            )

        brokenLog.arm("process start")
        runCurrent()

        val written = crashes.pendingFiles().map { crashes.read(it).summary }
        assertEquals(2, written.size)
        assertTrue(written.all { it.contains("The monthly reminder failed while") })
    }
}
