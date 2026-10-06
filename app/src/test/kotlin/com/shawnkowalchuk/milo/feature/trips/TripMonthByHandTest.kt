package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.ByHandMark
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripEdit
import com.shawnkowalchuk.milo.data.trip.TypedTrip
import com.shawnkowalchuk.milo.data.trip.editedTrip
import com.shawnkowalchuk.milo.data.trip.restoredTrip
import com.shawnkowalchuk.milo.data.trip.todayTrips
import com.shawnkowalchuk.milo.data.trip.tripAddedByHand
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Trips screen's month once trips can be added and edited by hand: such a trip is counted
 * like any other, and its row says that its figures are Shawn's own. Times are Edmonton
 * wall-clock times on Monday 5 October 2026.
 */
class TripMonthByHandTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    /** A trip MilO recorded: 12 km of Business in the morning. */
    private val recorded =
        Trip(
            id = 1,
            startedAtMs = local("2026-10-05T08:14:27"),
            endedAtMs = local("2026-10-05T08:39:02"),
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = 12_000.0,
            startLatitude = 53.5,
            startLongitude = -113.5,
            endLatitude = 53.3,
            endLongitude = -113.6,
            startAddress = "12 Shop Rd, Edmonton",
            endAddress = "48 Main St, Leduc",
            category = TripCategory.BUSINESS,
        )

    /** The same trip after its distance was put right by hand: 15 km. */
    private val edited =
        editedTrip(recorded, TripEdit(distanceMetres = 15_000.0), DEFAULT_WORK_SCHEDULE, edmonton)
            .copy(id = 2)

    /** A trip MilO missed, typed in: 20 km of Business before noon, with one address. */
    private val added =
        tripAddedByHand(
            TypedTrip(
                startedAtMs = local("2026-10-05T10:00:00"),
                endedAtMs = local("2026-10-05T10:40:00"),
                distanceMetres = 20_000.0,
                startAddress = "Shop",
                endAddress = null,
                category = null,
            ),
            DEFAULT_WORK_SCHEDULE,
            edmonton,
        ).copy(id = 3)

    private fun summary(trips: List<Trip>, showLeftOut: Boolean = false): MonthSummary =
        monthSummary(
            trips = trips,
            zone = edmonton,
            showLeftOut = showLeftOut,
            liveTripId = null,
            liveDistanceMetres = null,
            liveStart = null,
        )

    private fun line(trip: Trip, showLeftOut: Boolean = false): TripLine =
        summary(listOf(trip), showLeftOut).days.single().trips.single()

    // ---- Counted like any other -----------------------------------------------------------------

    @Test
    fun `trips added and edited by hand are in the month's and the day's totals`() {
        val month = summary(listOf(recorded, edited, added))

        // 12 km recorded, 15 km as edited (not the 12 km it was recorded with), 20 km typed in.
        assertEquals(Tally(3, 470), month.totals.business)
        assertEquals(3, month.days.single().sessionCount)
        assertEquals(470, month.days.single().businessTenths)
    }

    @Test
    fun `they are in today's totals on Home and on the Android Auto screen too`() {
        val today = todayTrips(listOf(recorded, edited, added))

        assertEquals(3, today.count)
        assertEquals(470, today.totalTenths)
        assertEquals(Tally(3, 470), today.totals.business)
    }

    @Test
    fun `a trip added by hand that is deleted leaves the totals like any other`() {
        val month = summary(listOf(recorded, added.copy(status = TripStatus.DELETED)))

        assertEquals(Tally(1, 120), month.totals.business)
        assertEquals(1, month.hiddenLeftOut)
    }

    // ---- The mark -------------------------------------------------------------------------------

    @Test
    fun `a row says whether its trip was added or edited by hand, or nothing at all`() {
        assertNull(line(recorded).mark)
        assertNull(line(recorded).byHandNoteRes())
        assertEquals(ByHandMark.EDITED, line(edited).mark)
        assertEquals(R.string.trips_mark_edited, line(edited).byHandNoteRes())
        assertEquals(ByHandMark.ADDED, line(added).mark)
        assertEquals(R.string.trips_mark_added, line(added).byHandNoteRes())
    }

    @Test
    fun `a trip marked Business or Personal by hand is not marked as edited`() {
        val marked =
            editedTrip(
                recorded,
                TripEdit(category = TripCategory.PERSONAL),
                DEFAULT_WORK_SCHEDULE,
                edmonton,
            )

        assertNull(line(marked).mark)
    }

    @Test
    fun `a deleted trip keeps its mark, so that it can be recognised`() {
        val deleted = line(added.copy(status = TripStatus.DELETED), showLeftOut = true)

        assertEquals(TripKind.DELETED, deleted.kind)
        assertEquals(R.string.trips_mark_added, deleted.byHandNoteRes())
    }

    @Test
    fun `a trip that was restored to its recorded values carries no mark`() {
        val restored = checkNotNull(restoredTrip(edited, DEFAULT_WORK_SCHEDULE, edmonton))

        assertNull(line(restored).mark)
        assertEquals(12_000.0, line(restored).distanceMetres)
    }

    // ---- What a row offers ----------------------------------------------------------------------

    @Test
    fun `only a counted trip can be opened for an edit`() {
        assertTrue(line(recorded).editable)
        assertTrue(line(added).editable)
        assertFalse(line(recorded.copy(status = TripStatus.DELETED), showLeftOut = true).editable)
        assertFalse(line(recorded.copy(status = TripStatus.DISCARDED), showLeftOut = true).editable)

        val recording = recorded.copy(status = TripStatus.OPEN, endedAtMs = null)
        assertEquals(false, summary(listOf(recording)).inProgress?.editable)
    }

    // ---- Where it went --------------------------------------------------------------------------

    @Test
    fun `a trip added by hand shows what was typed, and says which address was left empty`() {
        assertEquals(
            PlacesText.FromTo(
                PlaceSide.Address("Shop"),
                PlaceSide.Words(R.string.trips_place_left_blank),
            ),
            line(added).placesText(),
        )
        assertEquals(
            PlacesText.Sentence(R.string.trips_addresses_left_blank),
            line(added.copy(startAddress = null)).placesText(),
        )
    }

    // ---- Back from the edit screen --------------------------------------------------------------

    @Test
    fun `after a save the month the trip is in is shown, whichever month was on screen`() {
        val october = YearMonth.of(2026, 10)

        // Added for 29 September while October was on screen.
        assertEquals(
            YearMonth.of(2026, 9),
            monthOfSavedTrip(local("2026-09-29T09:00:00"), edmonton, current = october),
        )
        // Added for today while September was being looked at.
        assertEquals(
            october,
            monthOfSavedTrip(local("2026-10-05T09:00:00"), edmonton, current = october),
        )
    }

    @Test
    fun `the month of a saved trip is the month it starts in, in the phone's time zone`() {
        // 23:30 on 30 September in Edmonton is already October in UTC, and ends in October.
        val lastEvening = local("2026-09-30T23:30:00")

        assertEquals(
            YearMonth.of(2026, 9),
            monthOfSavedTrip(lastEvening, edmonton, current = YearMonth.of(2026, 10)),
        )
        assertEquals(
            YearMonth.of(2026, 10),
            monthOfSavedTrip(lastEvening, ZoneId.of("UTC"), current = YearMonth.of(2026, 10)),
        )
    }

    @Test
    fun `no save leads past the current month`() {
        // The form refuses a trip in the future, so this takes a clock that was set back.
        assertEquals(
            YearMonth.of(2026, 10),
            monthOfSavedTrip(local("2026-11-02T09:00:00"), edmonton, YearMonth.of(2026, 10)),
        )
    }
}
