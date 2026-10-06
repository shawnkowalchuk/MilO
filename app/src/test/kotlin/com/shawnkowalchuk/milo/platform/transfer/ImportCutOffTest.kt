package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sentReports
import com.shawnkowalchuk.milo.data.transfer.somePoints
import com.shawnkowalchuk.milo.data.transfer.withoutIds
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An import that MilO's process is ended in the middle of, and the process start after it.
 *
 * The trips and the raw points are in two database files, replaced one after the other. A
 * process that is ended in between has stored the file's trips and still holds the points of
 * the trips that are gone, and says nothing; the next start has to find that out and put it
 * right (`ImportCutOffCase` is the phone and the file).
 */
class ImportCutOffTest : ImportCutOffCase() {
    @Test
    fun `ended between the trips and the points, it leaves a note and a line in the log`() =
        runTest {
            world.points.endProcessWhileReplacing = true

            importInAProcessThatIsEnded()

            // What the phone is left with: the file's trips, and the points of trips 40 and 50,
            // which are gone.
            assertEquals(everyKindOfTrip, world.main.trips)
            assertEquals(ownPoints, world.points.points)
            assertEquals(50L, world.incoming.begun()?.highestTripId)
            assertNotNull(world.incoming.waiting())
            val said = world.main.logLines.single()
            assertEquals(EventCategory.REPORT, said.category)
            assertTrue(said.message, said.message.startsWith("An import has replaced the 2 trips"))
            assertEquals(said.message, world.incoming.begun()?.said)
        }

    @Test
    fun `the next start finishes it from the kept copy, and says so`() = runTest {
        world.points.endProcessWhileReplacing = true
        importInAProcessThatIsEnded()

        val status = nextStart()

        assertEquals(everyKindOfTrip, world.main.trips)
        assertEquals(sentReports, world.main.sentReports)
        assertEquals(somePoints.withoutIds(), storedPoints())
        val done = (status.outcome as TransferOutcome.Imported).done
        assertTrue(done.afterCutOff)
        assertEquals(PointsTaken.TAKEN, done.pointsTaken)
        assertEquals(4L, done.points)
        assertEquals(SettingsTaken.TAKEN, done.settings)
        assertEquals("Sam Driver", world.settings.current().reportName)
        assertNull(world.incoming.begun())
        assertNull(world.incoming.waiting())
        val line = world.lines(EventCategory.REPORT).single()
        assertTrue(line, line.contains("has finished it at this start"))
        assertTrue(line, line.contains("4 raw GPS points were stored"))
        assertTrue(line, line.contains("MilO-before-import-${world.nowMs}.json"))
        assertTrue(world.lines(EventCategory.ERROR).isEmpty())
        assertEquals(listOf("data imported"), world.toldAfterImport)
        // What the phone held before is still there to be put back.
        val (kept, keptPoints) = exportIn(world.safetyCopies.all().single().file.readText())
        assertEquals(ownTrips, kept.trips)
        assertEquals(ownPoints.withoutIds(), keptPoints)
    }

    @Test
    fun `a start after that finds nothing to do`() = runTest {
        world.points.endProcessWhileReplacing = true
        importInAProcessThatIsEnded()
        nextStart()
        val lines = world.log.entries.size

        val status = nextStart()

        assertEquals(TransferStatus(), status)
        assertEquals(lines, world.log.entries.size)
        assertEquals(4, world.points.points.size)
    }

    @Test
    fun `a trip that started since keeps its points, and is not in the way`() = runTest {
        world.points.endProcessWhileReplacing = true
        importInAProcessThatIsEnded()
        // The truck connected and started this process: trip 51 is being recorded.
        val open = recordedTrip.copy(id = 51, status = TripStatus.OPEN, endedAtMs = null)
        val itsFix = RawPoint(3, 51, 3_000, 30, 51.2, -114.2, 5f, 1f)
        world.main.trips += open
        world.points.points += itsFix
        world.tripInProgress = true

        val status = nextStart()

        assertTrue((status.outcome as TransferOutcome.Imported).done.afterCutOff)
        assertEquals(everyKindOfTrip + open, world.main.trips)
        assertTrue(itsFix in world.points.points)
        assertEquals(
            somePoints.withoutIds() + itsFix.copy(id = 0),
            storedPoints().sortedBy {
                it.tripId
            },
        )
    }

    @Test
    fun `ended before the trips were replaced, nothing is changed and the start says so`() =
        runTest {
            world.main.endProcessWhileReplacing = true
            importInAProcessThatIsEnded()
            // The note is there, and the line that the replacing writes is not.
            assertNotNull(world.incoming.begun())
            assertTrue(world.main.logLines.isEmpty())

            val status = nextStart()

            assertEquals(TransferOutcome.CutOff(CutOffImport.NOTHING_REPLACED), status.outcome)
            nothingWasChanged()
            assertNull(world.incoming.begun())
            assertNull(world.incoming.waiting())
            assertTrue(
                "Its safety copy held what the phone holds",
                world.safetyCopies.all().isEmpty(),
            )
            val line = world.lines(EventCategory.REPORT).single()
            assertTrue(line, line.contains("before the trips were replaced"))
            assertTrue(world.toldAfterImport.isEmpty())
        }

    @Test
    fun `where nothing was replaced, a trip with an id the file uses keeps its points`() = runTest {
        // The file's trips go up to 9. This phone's go up to 3, so its next trip is trip 4.
        world.main.trips = listOf(recordedTrip.copy(id = 2), recordedTrip.copy(id = 3))
        world.points.points = listOf(RawPoint(1, 2, 1_000, 10, 51.0, -114.0, 5f, 1f))
        world.main.endProcessWhileReplacing = true
        importInAProcessThatIsEnded()
        assertEquals(9L, world.incoming.begun()?.highestTripId)
        val itsFix = RawPoint(2, 4, 3_000, 30, 51.2, -114.2, 5f, 1f)
        world.main.trips += recordedTrip.copy(id = 4, status = TripStatus.OPEN, endedAtMs = null)
        world.points.points += itsFix

        nextStart()

        assertEquals(2, world.points.points.size)
        assertTrue(itsFix in world.points.points)
    }

    @Test
    fun `without a copy that can be used, the points of the trips that are gone are removed`() =
        runTest {
            for (spoil in listOf<() -> Unit>(
                { world.incoming.clear() },
                { world.incoming.take(ByteArrayInputStream("not what was checked".toByteArray())) },
            )) {
                world.main.trips = ownTrips
                world.points.points = ownPoints
                world.points.endProcessWhileReplacing = true
                importInAProcessThatIsEnded()
                val ofANewTrip = RawPoint(3, 51, 3_000, 30, 51.2, -114.2, 5f, 1f)
                world.points.points += ofANewTrip
                spoil()

                val status = nextStart()

                assertEquals(TransferOutcome.CutOff(CutOffImport.POINTS_REMOVED), status.outcome)
                assertEquals(everyKindOfTrip, world.main.trips)
                assertEquals(listOf(ofANewTrip), world.points.points)
                assertNull(world.incoming.begun())
                assertNull(world.incoming.waiting())
                val line = world.lines(EventCategory.REPORT).last()
                assertTrue(line, line.contains("the 2 raw GPS points of the trips that are gone"))
            }
        }

    @Test
    fun `with no note, a copy of a file whose question was never answered is removed`() = runTest {
        world.incoming.take(ByteArrayInputStream("an export that was picked".toByteArray()))

        val status = nextStart()

        assertEquals(TransferStatus(), status)
        assertNull(world.incoming.waiting())
        assertTrue(world.log.entries.isEmpty())
        nothingWasChanged()
    }
}
