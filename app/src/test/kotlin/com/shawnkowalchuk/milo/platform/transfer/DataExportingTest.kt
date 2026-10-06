package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.LastExport
import com.shawnkowalchuk.milo.data.settings.TransferredTruck
import com.shawnkowalchuk.milo.data.transfer.ExportContents
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sentReports
import com.shawnkowalchuk.milo.data.transfer.somePoints
import com.shawnkowalchuk.milo.data.transfer.withoutIds
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** "Export all data": what is written, what is recorded about it, and what stops it. */
class DataExportingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val world by lazy {
        TransferWorld(temporaryFolder.root).apply {
            main.trips = everyKindOfTrip
            main.sentReports = sentReports
            points.points = somePoints
            runBlocking { settings.setTruck("AA:BB:CC:DD:EE:FF", "Work truck", 7) }
        }
    }

    private val file = "content://files/MilO-export.json"

    @Test
    fun `an export holds every trip, every sent report, the settings and every point`() = runTest {
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        val (export, points) = exportIn(world.documents.textOf(file))
        assertEquals(everyKindOfTrip, export.trips)
        assertEquals(sentReports, export.sentReports)
        assertEquals(somePoints.withoutIds(), points)
        assertEquals(TransferredTruck("AA:BB:CC:DD:EE:FF", "Work truck"), export.settings?.truck)
        assertEquals(world.nowMs, export.exportedAtMs)
        val contents = ExportContents(trips = 8, sentReports = 3, points = 4, settings = true)
        assertEquals(TransferOutcome.Exported(contents), status.outcome)
    }

    @Test
    fun `the truck's association never leaves the phone in an export`() = runTest {
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        transfer.settled()

        val text = world.documents.textOf(file)
        assertFalse(text, text.contains("ssociation\":"))
        assertTrue(
            text.contains("\"truck\":{\"address\":\"AA:BB:CC:DD:EE:FF\",\"name\":\"Work truck\"}"),
        )
    }

    @Test
    fun `an export without the points holds none, and says so`() = runTest {
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = false)
        val status = transfer.settled()

        val (export, points) = exportIn(world.documents.textOf(file))
        assertNull(export.pointCount)
        assertTrue(points.isEmpty())
        assertEquals(everyKindOfTrip, export.trips)
        assertNull((status.outcome as TransferOutcome.Exported).contents.points)
    }

    @Test
    fun `the date of the export is stored, and the log says what the file holds`() = runTest {
        val transfer = world.transfer(backgroundScope)
        assertNull(world.settings.current().lastExport)

        transfer.exportTo(file, includePoints = false)
        transfer.settled()

        assertEquals(
            LastExport(world.nowMs, withPoints = false),
            world.settings.current().lastExport,
        )
        val line = world.lines(EventCategory.REPORT).single()
        assertTrue(line, line.contains("8 trips, 3 sent reports, no raw GPS points"))
        assertTrue(world.lines(EventCategory.ERROR).isEmpty())
    }

    @Test
    fun `no export is made while a trip is being recorded, and no file is touched`() = runTest {
        world.tripInProgress = true
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        assertEquals(TransferOutcome.Refused(TransferRefusal.TripInProgress), status.outcome)
        assertTrue(world.documents.files.isEmpty())
        assertNull(world.settings.current().lastExport)
    }

    @Test
    fun `a trip that storage holds open stops the export too, and the empty file is removed`() =
        runTest {
            world.main.trips +=
                recordedTrip.copy(id = 20, status = TripStatus.OPEN, endedAtMs = null)
            val transfer = world.transfer(backgroundScope)

            transfer.exportTo(file, includePoints = true)
            val status = transfer.settled()

            assertEquals(TransferOutcome.Refused(TransferRefusal.TripInProgress), status.outcome)
            assertEquals(listOf(file), world.documents.deleted)
            assertNull(world.settings.current().lastExport)
        }

    @Test
    fun `an export that breaks off is removed, said, and written to the log`() = runTest {
        world.documents.failWritingAfterBytes = 600
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        assertEquals(TransferOutcome.Refused(TransferRefusal.ExportNotWritten), status.outcome)
        assertEquals(listOf(file), world.documents.deleted)
        assertNull(world.settings.current().lastExport)
        val error = world.lines(EventCategory.ERROR).single()
        assertTrue(error, error.contains("writing an export. The unfinished file was removed"))
        assertTrue(world.lines(EventCategory.REPORT).isEmpty())
    }

    @Test
    fun `an unfinished file that cannot be removed is called what it is`() = runTest {
        world.documents.failWritingAfterBytes = 600
        world.documents.canDelete = false
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        assertEquals(TransferOutcome.Refused(TransferRefusal.ExportLeftUnfinished), status.outcome)
        assertTrue(world.lines(EventCategory.ERROR).single().contains("could NOT be removed"))
    }

    @Test
    fun `a trip that starts while Save is pressed leaves no empty file behind`() = runTest {
        // The file picker makes the file, empty, before MilO is asked to write to it.
        world.documents.files[file] = ByteArray(0)
        world.tripInProgress = true
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        assertEquals(TransferOutcome.Refused(TransferRefusal.TripInProgress), status.outcome)
        assertTrue(world.documents.files.isEmpty())
        assertEquals(listOf(file), world.documents.deleted)
    }

    @Test
    fun `an empty file that cannot be removed again is called what it is`() = runTest {
        world.documents.files[file] = ByteArray(0)
        world.documents.canDelete = false
        world.tripInProgress = true
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        assertEquals(TransferOutcome.Refused(TransferRefusal.ExportLeftUnfinished), status.outcome)
        assertTrue(world.lines(EventCategory.REPORT).single().contains("could not be removed"))
    }

    @Test
    fun `points that are stored for no closed trip are passed over, counted and said`() = runTest {
        // Left behind by trips that are gone, as after Android put the trips back over an
        // installation that kept its points; and the first fix of a trip that has just begun.
        world.points.points +=
            listOf(
                somePoints.first().copy(id = 97, tripId = 77),
                somePoints.first().copy(id = 98, tripId = 77),
                somePoints.first().copy(id = 99, tripId = 70),
            )
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        val (export, points) = exportIn(world.documents.textOf(file))
        assertEquals(somePoints.withoutIds(), points)
        assertEquals(4L, export.pointCount)
        val contents = ExportContents(trips = 8, sentReports = 3, points = 4, settings = true)
        assertEquals(TransferOutcome.Exported(contents, pointsLeftOut = 3), status.outcome)
        val line = world.lines(EventCategory.REPORT).single()
        assertTrue(line, line.contains("Left out of the file: 3 raw GPS points"))
        assertTrue(line, line.contains("(trip ids: 70, 77)"))
        assertTrue(world.documents.deleted.isEmpty())
    }

    @Test
    fun `an export without the points does not look for points to pass over`() = runTest {
        world.points.points += somePoints.first().copy(id = 99, tripId = 77)
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = false)
        val status = transfer.settled()

        assertEquals(0L, (status.outcome as TransferOutcome.Exported).pointsLeftOut)
        assertFalse(world.lines(EventCategory.REPORT).single().contains("Left out"))
    }

    @Test
    fun `with settings that cannot be read the trips are exported all the same`() = runTest {
        val unreadable = TransferWorld(temporaryFolder.newFolder(), settingsUnreadable = true)
        unreadable.main.trips = everyKindOfTrip
        val transfer = unreadable.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        val status = transfer.settled()

        val (export, _) = exportIn(unreadable.documents.textOf(file))
        assertEquals(everyKindOfTrip, export.trips)
        assertNull(export.settings)
        assertFalse((status.outcome as TransferOutcome.Exported).contents.settings)
        // The date could not be stored either, which is written down and changes nothing.
        assertEquals(1, unreadable.lines(EventCategory.ERROR).size)
    }

    @Test
    fun `one thing at a time`() = runTest {
        val firstMayWrite = CountDownLatch(1)
        world.documents.holdWritingUntil = firstMayWrite
        val transfer = world.transfer(backgroundScope)

        transfer.exportTo(file, includePoints = true)
        // The first export is still under way: a second press, and a pick for an import, do
        // nothing at all.
        transfer.exportTo("content://files/second.json", includePoints = true)
        transfer.offerImport(file)
        assertEquals(TransferWork.EXPORTING, transfer.status.value.working)
        firstMayWrite.countDown()
        val status = transfer.settled()

        assertEquals(setOf(file), world.documents.files.keys)
        assertTrue(status.outcome is TransferOutcome.Exported)
        assertNull(status.offer)
    }
}
