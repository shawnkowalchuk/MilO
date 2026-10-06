package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L
private const val MORNING_MS = 1_791_028_800_000L

/**
 * Which trips count, and what today adds up to. The home screen and the Android Auto screen
 * both show these figures, and the Trips screen counts a month by the same rule.
 */
class TripTotalsTest {
    private var nextId = 1L

    /** A trip that started [startMinute] minutes into the morning and lasted [minutes]. */
    private fun trip(
        startMinute: Long,
        minutes: Long,
        metres: Double,
        status: TripStatus = TripStatus.FINISHED,
    ): Trip {
        val startedAtMs = MORNING_MS + startMinute * MINUTE_MS
        return Trip(
            id = nextId++,
            startedAtMs = startedAtMs,
            endedAtMs = (startedAtMs + minutes * MINUTE_MS).takeIf { status != TripStatus.OPEN },
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = metres,
        )
    }

    // ---- What counts ------------------------------------------------------------------------------

    @Test
    fun `only a finished trip counts`() {
        assertTrue(trip(0, 20, 5_000.0, TripStatus.FINISHED).isCounted)
        assertFalse(trip(0, 20, 5_000.0, TripStatus.OPEN).isCounted)
        assertFalse(trip(0, 20, 150.0, TripStatus.DISCARDED).isCounted)
        assertFalse(trip(0, 20, 5_000.0, TripStatus.DELETED).isCounted)
    }

    @Test
    fun `today leaves out the deleted, the discarded and the trip in progress`() {
        val startedToday =
            listOf(
                trip(0, 25, 20_000.0),
                trip(60, 30, 21_249.0),
                // Too short, or a false start: kept in storage, never counted.
                trip(120, 2, 150.0, TripStatus.DISCARDED),
                // Deleted on the Trips screen: kept in storage too, and out of every total.
                trip(180, 40, 30_000.0, TripStatus.DELETED),
                // The trip in progress has its own card, and its stored distance is 0 anyway.
                trip(240, 0, 0.0, TripStatus.OPEN),
            )

        val today = todayTrips(startedToday)

        assertEquals(2, today.count)
        assertEquals(41_249.0, today.totalMetres, 0.0)
        assertEquals(55 * MINUTE_MS, today.driveTimeMs)
    }

    @Test
    fun `a day without finished trips adds up to nothing`() {
        val nothing = todayTrips(emptyList())
        val onlyLeftOut = todayTrips(listOf(trip(0, 20, 9_000.0, TripStatus.DELETED)))

        for (today in listOf(nothing, onlyLeftOut)) {
            assertEquals(0, today.count)
            assertEquals(0.0, today.totalMetres, 0.0)
            assertEquals(0L, today.driveTimeMs)
            assertTrue(today.sessions.isEmpty())
        }
    }

    @Test
    fun `a trip that is restored, or counted after all, is in today's totals again`() {
        val deleted = trip(0, 20, 8_200.0, TripStatus.DELETED)
        val discarded = trip(60, 5, 250.0, TripStatus.DISCARDED)
        assertEquals(0, todayTrips(listOf(deleted, discarded)).count)

        // What Restore and "Count this trip" do to a row: the status, and nothing else.
        val today =
            todayTrips(
                listOf(
                    deleted.copy(status = TripStatus.FINISHED),
                    discarded.copy(status = TripStatus.FINISHED),
                ),
            )

        assertEquals(2, today.count)
        assertEquals(8_450.0, today.totalMetres, 0.0)
        assertEquals(25 * MINUTE_MS, today.driveTimeMs)
    }

    // ---- The sessions -----------------------------------------------------------------------------

    @Test
    fun `each session carries its own times and kilometres, newest first`() {
        val first = trip(0, 25, 12_400.0)
        val second = trip(90, 10, 3_100.0)

        // Given oldest first, to show that the order is made here and not taken from storage.
        val today = todayTrips(listOf(first, second))

        assertEquals(
            listOf(
                TodaySession(second.id, second.startedAtMs, second.endedAtMs, 3_100.0),
                TodaySession(first.id, first.startedAtMs, first.endedAtMs, 12_400.0),
            ),
            today.sessions,
        )
        assertEquals(listOf(10 * MINUTE_MS, 25 * MINUTE_MS), today.sessions.map { it.driveTimeMs })
    }

    @Test
    fun `the total is added up in metres, so the rounding of single trips does not accumulate`() {
        // Each prints as 0.1 km, three of them as 0.4 km: 149 m each is 447 m.
        val today = todayTrips(List(3) { trip(it * 10L, 5, 149.0) })

        assertEquals(447.0, today.totalMetres, 0.0)
    }

    @Test
    fun `a trip whose stored start is later than its end has no drive time, not a negative one`() {
        // The phone corrected its clock during the trip (APP_ENCYCLOPEDIA, GPS recording).
        val backwards = trip(60, 20, 5_000.0).let { it.copy(endedAtMs = it.startedAtMs - 1_000) }
        val ordinary = trip(120, 30, 7_000.0)

        val today = todayTrips(listOf(backwards, ordinary))

        assertEquals(30 * MINUTE_MS, today.driveTimeMs)
        assertEquals(12_000.0, today.totalMetres, 0.0)
    }

    @Test
    fun `a finished trip without an end counts with no drive time`() {
        // Storage should never produce it; if it does, the day's figures must still add up.
        val endless = trip(0, 20, 5_000.0).copy(endedAtMs = null)

        assertEquals(0L, todayTrips(listOf(endless)).driveTimeMs)
        assertEquals(1, todayTrips(listOf(endless)).count)
    }
}
