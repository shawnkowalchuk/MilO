package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.transfer.ImportBegun
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.fileOf
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
 * The edges of finishing an import that was cut off: a start that is itself ended or fails
 * while it finishes, how often it is tried, and the note that says an import had begun.
 */
class ImportCutOffLimitsTest : ImportCutOffCase() {
    @Test
    fun `a start that is ended while it finishes is followed by one that only removes`() = runTest {
        world.points.endProcessWhileReplacing = true
        importInAProcessThatIsEnded()
        world.points.endProcessWhileReplacing = true
        nextStart()
        assertEquals(1, world.incoming.begun()?.tries)
        assertEquals(ownPoints, world.points.points)

        val status = nextStart()

        // The copy is as good as it was. It is not read a second time all the same.
        assertEquals(TransferOutcome.CutOff(CutOffImport.POINTS_REMOVED), status.outcome)
        assertTrue(world.points.points.isEmpty())
        assertNull(world.incoming.begun())
        assertNull(world.incoming.waiting())
    }

    @Test
    fun `a later start does not need the log's line, which may have been trimmed by then`() =
        runTest {
            world.points.endProcessWhileReplacing = true
            importInAProcessThatIsEnded()
            world.points.endProcessWhileReplacing = true
            nextStart()
            world.main.logLines = emptyList()

            val status = nextStart()

            // Not "nothing was replaced": the first start saw the line and counted its try.
            assertEquals(TransferOutcome.CutOff(CutOffImport.POINTS_REMOVED), status.outcome)
            assertEquals(everyKindOfTrip, world.main.trips)
            assertTrue(world.points.points.isEmpty())
        }

    @Test
    fun `after two starts that were ended over it, the import is left as it is and said`() =
        runTest {
            world.points.endProcessWhileReplacing = true
            importInAProcessThatIsEnded()
            world.points.endProcessWhileReplacing = true
            nextStart()
            world.points.endProcessWhileRemoving = true
            nextStart()
            assertEquals(2, world.incoming.begun()?.tries)

            val status = nextStart()

            assertEquals(TransferOutcome.CutOff(CutOffImport.NOT_FINISHED), status.outcome)
            assertEquals(ownPoints, world.points.points)
            assertNull(world.incoming.begun())
            assertNull(world.incoming.waiting())
            assertTrue(world.lines(EventCategory.ERROR).single().contains("left as it is"))
            // And no start after it meets it again.
            assertEquals(TransferStatus(), nextStart())
        }

    @Test
    fun `points that can be neither stored nor removed are put right by the next start`() =
        runTest {
            // Replacing the points removes the old ones first, so it fails with the removing.
            world.points.failRemoving = true
            world.documents.files[picked] = fileOf().toByteArray()
            val first = world.transfer(backgroundScope)
            first.offerImport(picked)
            first.settled()
            first.confirmImport()
            val failed = (first.settled().outcome as TransferOutcome.Imported).done
            assertEquals(PointsTaken.NOT_STORED, failed.pointsTaken)
            assertEquals(ownPoints, world.points.points)
            // The note and the copy stay, because the phone is not yet as the card says.
            assertNotNull(world.incoming.begun())
            assertNotNull(world.incoming.waiting())
            world.points.failRemoving = false

            val status = nextStart()

            assertEquals(
                PointsTaken.TAKEN,
                (status.outcome as TransferOutcome.Imported).done.pointsTaken,
            )
            assertEquals(somePoints.withoutIds(), storedPoints())
            assertNull(world.incoming.begun())
        }

    @Test
    fun `the steps after the trips can be taken twice`() = runTest {
        // Ended after the points were stored and before the note was taken away.
        world.points.endProcessWhileReplacing = true
        importInAProcessThatIsEnded()
        val note = world.incoming.begun() ?: error("no note")
        nextStart()
        world.incoming.take(ByteArrayInputStream(fileOf().toByteArray()))
        world.incoming.noteBegun(note)

        val status = nextStart()

        assertTrue((status.outcome as TransferOutcome.Imported).done.afterCutOff)
        assertEquals(everyKindOfTrip, world.main.trips)
        assertEquals(somePoints.withoutIds(), storedPoints())
        assertNull(world.incoming.begun())
    }

    @Test
    fun `a failure of the start's own work is said, written down, and tried again later`() =
        runTest {
            world.points.endProcessWhileReplacing = true
            importInAProcessThatIsEnded()
            world.incoming.clear()
            world.points.failRemoving = true

            val status = nextStart()

            assertEquals(TransferOutcome.CutOff(CutOffImport.NOT_FINISHED), status.outcome)
            assertTrue(world.lines(EventCategory.ERROR).single().contains("finishing an import"))
            assertEquals(1, world.incoming.begun()?.tries)
            world.points.failRemoving = false
            assertEquals(
                TransferOutcome.CutOff(CutOffImport.POINTS_REMOVED),
                nextStart().outcome,
            )
        }

    @Test
    fun `an import that is not cut off leaves no note, and its line in the log`() = runTest {
        world.documents.files[picked] = fileOf().toByteArray()
        val transfer = world.transfer(backgroundScope)
        transfer.offerImport(picked)
        transfer.settled()
        transfer.confirmImport()
        transfer.settled()

        assertNull(world.incoming.begun())
        assertEquals(1, world.main.logLines.size)
        assertEquals(TransferStatus(), nextStart())
    }

    @Test
    fun `an import that replaces nothing leaves no note either`() = runTest {
        world.documents.files[picked] = fileOf().toByteArray()
        val transfer = world.transfer(backgroundScope)
        transfer.offerImport(picked)
        transfer.settled()
        world.main.failNextInsert = IllegalStateException("database or disk is full")
        transfer.confirmImport()

        val refused = TransferOutcome.Refused(TransferRefusal.NothingReplaced)
        assertEquals(refused, transfer.settled().outcome)
        assertNull(world.incoming.begun())
        assertTrue(world.main.logLines.isEmpty())
        nothingWasChanged()
    }

    @Test
    fun `a note that cannot be understood is removed and written down, once`() = runTest {
        world.incoming.take(ByteArrayInputStream(fileOf().toByteArray()))
        world.incoming.noteBegun(ImportBegun(50, 1, 2, "a line"))
        temporaryFolder.root.resolve("import/begun.txt").writeText("not a note")

        val status = nextStart()

        assertEquals(TransferStatus(), status)
        assertEquals(1, world.lines(EventCategory.ERROR).size)
        assertNull(world.incoming.waiting())
        nothingWasChanged()
        nextStart()
        assertEquals(1, world.lines(EventCategory.ERROR).size)
    }
}
