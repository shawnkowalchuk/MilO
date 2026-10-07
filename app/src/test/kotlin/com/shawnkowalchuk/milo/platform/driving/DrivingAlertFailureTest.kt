package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogDao
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.process
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** An event log that cannot be written, as with a full disk. */
private object UnwritableLog : EventLogDao {
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
 * A failure inside the driving alert stays inside it. The alert runs in the process the trip
 * service runs in, and a report arrives about a minute into every drive, so an exception that
 * got out would end a recording for the sake of the safety net.
 *
 * In each test the alert's coroutines run in the test's background scope, and `runTest` fails a
 * test in which one of them ends with an exception. That a test passes at all is therefore the
 * proof that nothing got out.
 */
// runCurrent() is how a test lets the alert's and the controller's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class DrivingAlertFailureTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val world = workdayWorld()
    private val detection = FakeDetection()
    private val notification = FakeNotification()
    private val crashFiles by lazy { CrashFileStore(folder.root) }
    private var finished = 0

    private fun errors() = world.log.entries.filter { it.category == EventCategory.ERROR }

    @Test
    fun `a failure while a report is judged is logged, and the receiver is still let go`() =
        runTest {
            val (controller, service) = process(world)
            service.comesUpAtOnce = true
            world.truck.connected = true
            controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "the test")
            runCurrent()
            val unreadable = IllegalStateException("the trips table cannot be read")
            val alert =
                drivingAlert(
                    controller,
                    world,
                    detection,
                    notification,
                    crashFiles,
                    openTripStored = { throw unreadable },
                )

            // The report every truck drive brings, about a minute in.
            alert.onReports(listOf(ENTERED)) { finished++ }
            runCurrent()

            assertEquals(1, finished)
            val error = errors().single()
            assertEquals(
                "The driving alert failed while dealing with a report of driving",
                error.message,
            )
            assertTrue(error.detail.orEmpty().contains("the trips table cannot be read"))
            assertEquals(0, notification.posted)
            // The trip the truck started is still being recorded.
            assertNotNull(controller.activity.value.trip)
            assertTrue(service.recording)
        }

    @Test
    fun `when the event log fails as well, the failure goes to a crash file`() = runTest {
        val (controller, _) = process(world)
        val alert =
            drivingAlert(
                controller,
                world,
                detection,
                notification,
                crashFiles,
                log = UnwritableLog,
            )

        // Judged and shown; it is the line about it that cannot be written.
        alert.onReports(listOf(ENTERED)) { finished++ }
        runCurrent()

        assertEquals(1, finished)
        assertTrue(notification.showing)
        val record = crashFiles.read(crashFiles.pendingFiles().single())
        assertTrue(record.summary.contains("The driving alert failed while dealing with a report"))
        assertTrue(record.stackTrace.contains("the disk is full"))
    }

    @Test
    fun `a failure while the phone is asked is logged, and the next call asks again`() = runTest {
        val (controller, _) = process(world)
        val alert = drivingAlert(controller, world, detection, notification, crashFiles)
        detection.throwOnce = IllegalStateException("Play services fell over")

        alert.arm("process start")
        runCurrent()

        val error = errors().single()
        assertEquals(
            "The driving alert failed while looking at what to ask of the phone (process start)",
            error.message,
        )
        assertTrue(error.detail.orEmpty().contains("Play services fell over"))

        alert.arm("MilO in front")
        runCurrent()

        assertEquals(listOf("watch", "watch"), detection.asked)
        assertEquals(
            "Driving alert: watching for driving (MilO in front)",
            world.logged(EventCategory.DRIVING).single(),
        )
    }

    @Test
    fun `a failure while the alert is taken away for a trip does not touch the trip`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        drivingAlert(controller, world, detection, notification, crashFiles)
        notification.failNextWithdrawal = SecurityException("notifications are locked")

        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        assertEquals(
            "The driving alert failed while taking the alert away for a trip that began",
            errors().single().message,
        )
        assertNotNull(controller.activity.value.trip)
        assertTrue(service.recording)
    }
}
