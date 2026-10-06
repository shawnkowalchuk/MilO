package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three changes Shawn can make to a closed trip, as changes of its status: where each one
 * starts and where it leads, and which one a trip of each status is offered. That storage makes
 * a change only from the status it starts from is in `TripCorrectionsTest`, against the
 * stand-in trips table.
 */
class TripCorrectionTest {
    @Test
    fun `delete takes a finished trip out, and restore is its exact reverse`() {
        assertEquals(TripStatus.FINISHED, TripCorrection.DELETE.from)
        assertEquals(TripStatus.DELETED, TripCorrection.DELETE.to)

        assertEquals(TripCorrection.DELETE.to, TripCorrection.RESTORE.from)
        assertEquals(TripCorrection.DELETE.from, TripCorrection.RESTORE.to)
    }

    @Test
    fun `counting a discarded trip makes it a finished one`() {
        assertEquals(TripStatus.DISCARDED, TripCorrection.COUNT.from)
        assertEquals(TripStatus.FINISHED, TripCorrection.COUNT.to)
    }

    @Test
    fun `each closed status is offered exactly the change that starts from it`() {
        assertEquals(TripCorrection.DELETE, TripStatus.FINISHED.correctionOffered())
        assertEquals(TripCorrection.RESTORE, TripStatus.DELETED.correctionOffered())
        assertEquals(TripCorrection.COUNT, TripStatus.DISCARDED.correctionOffered())
    }

    @Test
    fun `nothing is offered for a trip that is still being recorded`() {
        assertNull(TripStatus.OPEN.correctionOffered())
    }

    @Test
    fun `no two changes start from the same status`() {
        // "The one change offered" would otherwise depend on the order the changes are
        // written in.
        val starts = TripCorrection.entries.map { it.from }

        assertEquals(starts.distinct(), starts)
    }

    @Test
    fun `no change ever leads to an open trip, or starts from one`() {
        // "At most one trip is open" is the trip rules' to keep. Nothing here may touch it.
        assertTrue(TripCorrection.entries.none { it.to == TripStatus.OPEN })
        assertTrue(TripCorrection.entries.none { it.from == TripStatus.OPEN })
    }
}
