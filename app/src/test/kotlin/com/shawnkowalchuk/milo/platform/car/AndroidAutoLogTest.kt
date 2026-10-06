package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.process
import com.shawnkowalchuk.milo.platform.trip.recordingManualTrip
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// The two values `CarConnection` gives on a phone.
private const val PROJECTION = 2
private const val NOT_CONNECTED = 0

/** Stands in for `CarConnection`: the test says what Android Auto reports, and when. */
private class FakeAndroidAuto : AndroidAutoSource {
    var watches = 0

    /** Set to make the start of the watch fail, as a fault inside the library would. */
    var failWith: Exception? = null
    private var onReport: ((Boolean, Int?) -> Unit)? = null

    override fun watch(onReport: (connected: Boolean, rawType: Int?) -> Unit) {
        watches++
        failWith?.let { throw it }
        this.onReport = onReport
    }

    fun reports(connected: Boolean) {
        checkNotNull(onReport) { "The watch has not begun" }(
            connected,
            if (connected) PROJECTION else NOT_CONNECTED,
        )
    }
}

/** An event log that cannot be written for its first [failures] lines, as with a full disk. */
private class FailingLog(private var failures: Int, private val log: FakeEventLogDao) :
    EventLogDao by log {
    override suspend fun insert(entry: EventLogEntry): Long {
        if (failures > 0) {
            failures--
            throw IOException("the disk is full")
        }
        return log.insert(entry)
    }
}

/**
 * The app-wide watch on Android Auto: a line for the first reading and for each change while no
 * trip is being recorded, silence while one is, and no failure that gets out.
 *
 * The watch's coroutine runs in the test's background scope, and `runTest` fails a test in
 * which it ends with an exception. That a test of a failure passes at all is therefore the
 * proof that nothing got out.
 */
// runCurrent() is how a test lets the watch's and the controller's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAutoLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val world = FakeWorld()
    private val androidAuto = FakeAndroidAuto()
    private val crashFiles by lazy { CrashFileStore(folder.root) }
    private var tripBeingRecorded = false

    private fun TestScope.watch(
        log: EventLogDao = world.log,
        crashFileStore: CrashFileStore = crashFiles,
        tripBeingRecorded: () -> Boolean = { this@AndroidAutoLogTest.tripBeingRecorded },
    ): AndroidAutoLog = AndroidAutoLog(
        source = androidAuto,
        // The tests run on one thread, which stands in for the main thread.
        mainThread = { work -> work.run() },
        tripBeingRecorded = tripBeingRecorded,
        eventLog = EventLogRepository(log),
        crashFileStore = crashFileStore,
        clock = { world.nowMs },
        scope = backgroundScope,
    )

    private fun lines() = world.logged(EventCategory.ANDROID_AUTO)

    private fun errors() = world.log.entries.filter { it.category == EventCategory.ERROR }

    @Test
    fun `the first reading and each change are written, dated when they arrived`() = runTest {
        watch().start()
        androidAuto.reports(connected = false)
        world.nowMs += 60_000
        androidAuto.reports(connected = true)
        world.nowMs += 60_000
        androidAuto.reports(connected = false)
        world.nowMs += 60_000
        runCurrent()

        assertEquals(
            listOf(
                "Android Auto is not connected (CarConnection reports type 0). First reading " +
                    "since MilO's process started. No trip is being recorded",
                "Android Auto connected (CarConnection reports type 2). No trip is being " +
                    "recorded, and Android Auto does not start one",
                "Android Auto disconnected (CarConnection reports type 0). No trip is being " +
                    "recorded",
            ),
            lines(),
        )
        val times = world.log.entries.map { it.atMs }
        assertEquals(listOf(0L, 60_000L, 120_000L), times.map { it - times.first() })
        assertTrue(errors().isEmpty())
    }

    @Test
    fun `a report that repeats the last one writes nothing`() = runTest {
        watch().start()
        androidAuto.reports(connected = false)
        androidAuto.reports(connected = false)
        runCurrent()

        assertEquals(1, lines().size)
    }

    @Test
    fun `nothing is written while a trip is being recorded`() = runTest {
        watch().start()
        tripBeingRecorded = true
        androidAuto.reports(connected = false)
        androidAuto.reports(connected = true)
        runCurrent()

        assertTrue(lines().isEmpty())
    }

    @Test
    fun `a connection made during a trip and ended after it is written as a change`() = runTest {
        watch().start()
        tripBeingRecorded = true
        androidAuto.reports(connected = true)
        tripBeingRecorded = false
        androidAuto.reports(connected = false)
        runCurrent()

        assertEquals(
            "Android Auto disconnected (CarConnection reports type 0). No trip is being recorded",
            lines().single(),
        )
    }

    @Test
    fun `beside a real trip, the trip controller's line is the only one`() = runTest {
        val (controller, service) = recordingManualTrip(world)
        watch(tripBeingRecorded = { controller.activity.value.trip != null }).start()
        val startsAskedFor = service.startRequests.size

        // What the phone does when Android Auto connects in mid-trip: the trip service's watch
        // tells the trip controller, and the app-wide watch hears the same change.
        controller.onAndroidAuto(connected = true, "CarConnection reports type 2")
        androidAuto.reports(connected = true)
        runCurrent()

        assertEquals(
            listOf("CarConnection reports type 2: Android Auto is connected"),
            lines(),
        )
        assertNotNull(controller.activity.value.trip)
        assertEquals(startsAskedFor, service.startRequests.size)
    }

    @Test
    fun `with no trip, a report is written and starts nothing`() = runTest {
        val (controller, service) = process(world)
        watch(tripBeingRecorded = { controller.activity.value.trip != null }).start()
        val triggersBefore = world.logged(EventCategory.TRIGGER)

        androidAuto.reports(connected = false)
        androidAuto.reports(connected = true)
        runCurrent()

        assertEquals(2, lines().size)
        assertNull(controller.activity.value.trip)
        assertTrue(service.startRequests.isEmpty())
        assertTrue(world.openTrips.isEmpty())
        assertEquals(triggersBefore, world.logged(EventCategory.TRIGGER))
    }

    @Test
    fun `a second start does not watch twice`() = runTest {
        val watch = watch()
        watch.start()
        watch.start()

        assertEquals(1, androidAuto.watches)
    }

    @Test
    fun `a failure while the watch begins is logged and goes no further`() = runTest {
        androidAuto.failWith = IllegalStateException("the library fell over")

        watch().start()
        runCurrent()

        val error = errors().single()
        assertEquals(
            "The watch on Android Auto outside trips failed while starting to watch",
            error.message,
        )
        assertTrue(error.detail.orEmpty().contains("the library fell over"))
        assertTrue(lines().isEmpty())
    }

    @Test
    fun `a failure while a report is taken is logged, and a trip carries on`() = runTest {
        val (controller, service) = recordingManualTrip(world)
        watch(tripBeingRecorded = { throw IllegalStateException("the state cannot be read") })
            .start()

        androidAuto.reports(connected = true)
        runCurrent()

        assertEquals(
            "The watch on Android Auto outside trips failed while taking a report",
            errors().single().message,
        )
        assertNotNull(controller.activity.value.trip)
        assertTrue(service.recording)
    }

    @Test
    fun `when the event log fails, the failure goes to a crash file`() = runTest {
        watch(log = FailingLog(failures = 2, world.log)).start()

        // The line cannot be written, and neither can the line about that.
        androidAuto.reports(connected = false)
        runCurrent()

        val record = crashFiles.read(crashFiles.pendingFiles().single())
        assertTrue(
            record.summary.contains(
                "The watch on Android Auto outside trips failed while writing a line",
            ),
        )
        assertTrue(record.stackTrace.contains("the disk is full"))
        assertTrue(world.log.entries.isEmpty())
    }

    @Test
    fun `when the crash file fails as well, nothing gets out and the next line says so`() =
        runTest {
            // A folder that cannot be made: its parent is a file.
            val noFolder = CrashFileStore(File(folder.newFile("in-the-way"), "crashes"))
            watch(log = FailingLog(failures = 2, world.log), crashFileStore = noFolder).start()

            androidAuto.reports(connected = false)
            runCurrent()
            androidAuto.reports(connected = true)
            runCurrent()

            val written = world.log.entries.single()
            assertEquals(EventCategory.ANDROID_AUTO, written.category)
            assertTrue(written.message.startsWith("Android Auto connected"))
            assertEquals(
                "Before this line, 1 failure(s) of this watch could be written neither to the " +
                    "event log nor to a crash file.",
                written.detail,
            )
        }
}
