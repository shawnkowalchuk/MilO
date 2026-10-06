package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.transfer.fileOf
import com.shawnkowalchuk.milo.data.transfer.recordedTrip
import com.shawnkowalchuk.milo.data.transfer.sourceOf
import com.shawnkowalchuk.milo.data.trip.Trip
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * "Put that data back", after more than one import: each safety copy can be put back, and
 * putting one back never costs another.
 *
 * The phone starts with trip 40. Two wrong files are imported, one after the other: A, which
 * holds trip 71, and B, which holds trip 72. What has to stay within reach all the while is
 * the copy MilO kept before the first of them, because nothing else holds trip 40.
 */
class PuttingBackTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val own = listOf(recordedTrip.copy(id = 40, distanceMetres = 1_111.0))
    private val ownPoints = listOf(RawPoint(1, 40, 1_000, 10, 51.0, -114.0, 5f, 1f))
    private val ofFileA = listOf(recordedTrip.copy(id = 71))
    private val ofFileB = listOf(recordedTrip.copy(id = 72))

    private val world by lazy {
        TransferWorld(temporaryFolder.root).apply {
            main.trips = own
            points.points = ownPoints
        }
    }

    private val picked = "content://files/picked.json"

    private fun fileWith(trips: List<Trip>) =
        fileOf(sourceOf(trips = trips, reports = emptyList(), points = emptyList()))

    /** Imports a picked file a minute later, and answers when the safety copy was written. */
    private fun DataTransfer.import(text: String): Long {
        world.nowMs += 60_000
        world.documents.files[picked] = text.toByteArray()
        offerImport(picked)
        settled()
        confirmImport()
        assertTrue(settled().outcome is TransferOutcome.Imported)
        return world.nowMs
    }

    /** Puts the copy written at [writtenAtMs] back, a minute later. */
    private fun DataTransfer.putBack(writtenAtMs: Long): TransferStatus {
        world.nowMs += 60_000
        offerSafetyCopy(writtenAtMs)
        assertEquals(writtenAtMs, settled().offer?.safetyCopyAtMs)
        confirmImport()
        return settled()
    }

    private fun copies(): List<Long> = world.safetyCopies.all().map { it.writtenAtMs }

    private fun TestScope.afterTwoWrongImports(): Triple<DataTransfer, Long, Long> {
        val transfer = world.transfer(backgroundScope)
        val beforeA = transfer.import(fileWith(ofFileA))
        val beforeB = transfer.import(fileWith(ofFileB))
        assertEquals(listOf(beforeB, beforeA), copies())
        return Triple(transfer, beforeA, beforeB)
    }

    @Test
    fun `the copy from before the first of two imports can be put back at once`() = runTest {
        val (transfer, beforeA, beforeB) = afterTwoWrongImports()

        val status = transfer.putBack(beforeA)

        assertTrue(status.outcome is TransferOutcome.Imported)
        assertEquals(own, world.main.trips)
        assertEquals(ownPoints.map { it.copy(id = 0) }, world.points.points.map { it.copy(id = 0) })
        // The copy that was put back is gone, since the phone holds it again; the copy of
        // what the phone held a moment ago has taken its place, and no other copy went.
        assertEquals(listOf(world.nowMs, beforeB), copies())
    }

    @Test
    fun `stepping back one import at a time reaches what the phone first held`() = runTest {
        val (transfer, beforeA, beforeB) = afterTwoWrongImports()

        transfer.putBack(beforeB)
        assertEquals(ofFileA, world.main.trips)
        assertTrue("The copy from before the first import must stay", beforeA in copies())
        transfer.putBack(beforeA)

        assertEquals(own, world.main.trips)
    }

    @Test
    fun `putting copies back, however often, never pushes an older copy out`() = runTest {
        val (transfer, beforeA, _) = afterTwoWrongImports()
        transfer.import(fileWith(ofFileA))
        assertEquals(3, copies().size)

        // The newest each time, as the one button of an earlier build did: back and forth.
        repeat(4) { transfer.putBack(copies().first()) }

        assertEquals(3, copies().size)
        assertTrue("The only copy of trip 40 must stay", beforeA in copies())
        transfer.putBack(beforeA)
        assertEquals(own, world.main.trips)
    }

    @Test
    fun `a copy whose points could not be stored when it was put back is kept`() = runTest {
        val (transfer, beforeA, beforeB) = afterTwoWrongImports()
        val third = transfer.import(fileWith(ofFileA))
        world.points.failInsertOnceStoredAtLeast = 1

        val status = transfer.putBack(beforeA)

        val done = (status.outcome as TransferOutcome.Imported).done
        assertEquals(PointsTaken.NOT_STORED, done.pointsTaken)
        // Four now, and the oldest among them: it is the one that has to be put back again.
        assertEquals(listOf(world.nowMs, third, beforeB, beforeA), copies())
    }

    @Test
    fun `an import of a picked file still keeps the newest three copies only`() = runTest {
        val (transfer, beforeA, beforeB) = afterTwoWrongImports()

        val third = transfer.import(fileWith(ofFileA))
        val fourth = transfer.import(fileWith(ofFileB))

        assertEquals(listOf(fourth, third, beforeB), copies())
        assertTrue(beforeA !in copies())
    }
}
