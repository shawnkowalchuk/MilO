package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportMark
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which stored trips a report lists: counted, saved as Business, and started in the period.
 * (Where a period begins and ends is `ReportSelectionPeriodTest`.)
 */
class ReportSelectionTest {
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))

    @Test
    fun `a finished Business trip that started in the month is on the report`() {
        val trip = storedTrip("2026-10-05T08:14")

        val selection = selectForReport(listOf(trip), october, EDMONTON)

        val listed = selection.trips.single()
        assertEquals(trip.startedAtMs, listed.startedAtMs)
        assertEquals(trip.endedAtMs, listed.endedAtMs)
        assertEquals("12 Shop Rd, Edmonton", listed.from)
        assertEquals("48 Main St, Leduc", listed.to)
        assertEquals(12_340.0, listed.distanceMetres, 0.0)
        assertNull(listed.mark)
    }

    @Test
    fun `only counted trips are listed, not one in progress, discarded or deleted`() {
        val trips =
            listOf(
                storedTrip("2026-10-05T08:00", TripStatus.FINISHED),
                storedTrip("2026-10-05T09:00", TripStatus.OPEN),
                storedTrip("2026-10-05T10:00", TripStatus.DISCARDED),
                storedTrip("2026-10-05T11:00", TripStatus.DELETED),
            )

        val selection = selectForReport(trips, october, EDMONTON)

        assertEquals(listOf(at("2026-10-05T08:00")), starts(selection))
        assertTrue(selection.tripInProgress)
        // A discarded or a deleted trip is not counted as left out either: it is no trip.
        assertEquals(0, selection.personalLeftOut)
        assertEquals(0, selection.unsortedLeftOut)
    }

    @Test
    fun `only Business trips are listed, and what is left off is counted`() {
        val trips =
            listOf(
                storedTrip("2026-10-05T08:00", category = TripCategory.BUSINESS),
                storedTrip("2026-10-05T18:00", category = TripCategory.PERSONAL),
                storedTrip("2026-10-06T19:00", category = TripCategory.PERSONAL),
                storedTrip("2026-10-07T08:00", category = null),
                // Not counted, so not "left off" either, whatever it is saved as.
                storedTrip("2026-10-08T18:00", TripStatus.DELETED, TripCategory.PERSONAL),
            )

        val selection = selectForReport(trips, october, EDMONTON)

        assertEquals(listOf(at("2026-10-05T08:00")), starts(selection))
        assertEquals(2, selection.personalLeftOut)
        assertEquals(1, selection.unsortedLeftOut)
        assertFalse(selection.tripInProgress)
    }

    @Test
    fun `the Personal trips' kilometres are added up as the report adds up its own`() {
        val trips =
            listOf(
                storedTrip("2026-10-05T08:00", category = TripCategory.BUSINESS, metres = 50_000.0),
                // 12.34 and 5.86 km are printed as 12.3 and 5.9: 18.2, not the 18.20 of the
                // metres added up first.
                storedTrip("2026-10-05T18:00", category = TripCategory.PERSONAL, metres = 12_340.0),
                storedTrip("2026-10-06T19:00", category = TripCategory.PERSONAL, metres = 5_860.0),
                // Neither counted nor of the period, so in no figure.
                storedTrip("2026-10-08T18:00", TripStatus.DELETED, TripCategory.PERSONAL),
                storedTrip("2026-09-30T18:00", category = TripCategory.PERSONAL),
            )

        val selection = selectForReport(trips, october, EDMONTON)

        assertEquals(2, selection.personalLeftOut)
        assertEquals(182L, selection.personalTenths)
    }

    @Test
    fun `the trips are listed oldest first, whatever order storage hands them in`() {
        val trips =
            listOf(
                storedTrip("2026-10-06T13:00"),
                storedTrip("2026-10-05T15:30"),
                storedTrip("2026-10-06T08:00"),
                storedTrip("2026-10-05T08:14"),
            )
        // Two trips that start at the same instant keep the order they were stored in.
        val twin = trips[3].copy(id = 99, distanceMetres = 1.0)

        val selection = selectForReport(trips + twin, october, EDMONTON)

        assertEquals(
            listOf(
                "2026-10-05T08:14",
                "2026-10-05T08:14",
                "2026-10-05T15:30",
                "2026-10-06T08:00",
                "2026-10-06T13:00",
            )
                .map { at(it) },
            starts(selection),
        )
        assertEquals(listOf(12_340.0, 1.0), selection.trips.take(2).map { it.distanceMetres })
    }

    @Test
    fun `a trip added or edited by hand carries its mark, and one only marked Business does not`() {
        val trips =
            listOf(
                storedTrip("2026-10-05T08:00").copy(addedByHand = true),
                storedTrip("2026-10-05T09:00").copy(editedByHand = true),
                storedTrip("2026-10-05T10:00").copy(categorySetByHand = true),
            )

        val marks = selectForReport(trips, october, EDMONTON).trips.map { it.mark }

        assertEquals(listOf(ReportMark.ADDED, ReportMark.EDITED, null), marks)
    }

    @Test
    fun `a trip without an address is listed without one, and never with its position`() {
        val trips =
            listOf(
                storedTrip("2026-10-05T08:00", startAddress = null, endAddress = null),
                storedTrip("2026-10-05T09:00", endAddress = null),
                storedTrip("2026-10-05T10:00"),
                // Left off the report, so its missing address is nobody's concern.
                storedTrip(
                    "2026-10-05T18:00",
                    category = TripCategory.PERSONAL,
                    startAddress = null,
                ),
            )

        val selection = selectForReport(trips, october, EDMONTON)

        assertEquals(
            listOf(null, "12 Shop Rd, Edmonton", "12 Shop Rd, Edmonton"),
            selection.trips.map {
                it.from
            },
        )
        assertEquals(listOf(null, null, "48 Main St, Leduc"), selection.trips.map { it.to })
        assertEquals(2, selection.withoutAddress)
        // The stored trips have positions. Nothing of them reaches the report's trips.
        val written = selection.trips.joinToString()
        assertFalse(written, written.contains("53.5444") || written.contains("113.49"))
    }

    @Test
    fun `a period without trips has nothing on it and nothing left off`() {
        val selection = selectForReport(listOf(storedTrip("2026-09-15T08:00")), october, EDMONTON)

        assertEquals(ReportSelection(emptyList(), 0, 0, 0, false, personalTenths = 0), selection)
    }
}
