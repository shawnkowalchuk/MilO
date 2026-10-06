package com.shawnkowalchuk.milo.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an import refuses of a trip, a sent report, the settings and a raw point, one rule at a
 * time, each from a value that is otherwise right.
 */
class ExportChecksTest {
    private val trip = recordedTrip.toExported()
    private val report = sentReports.first().toExported()
    private val settings = changedSettings.toExported()

    @Test
    fun `what MilO itself stores has nothing wrong with it`() {
        for (stored in everyKindOfTrip) assertNull(stored.toExported().problem())
        for (stored in sentReports) assertNull(stored.toExported().problem())
        assertNull(settings.problem())
    }

    @Test
    fun `a trip whose id is no id, or would leave no number for the next trip`() {
        assertNotNull(trip.copy(id = 0).problem())
        assertNotNull(trip.copy(id = -4).problem())
        assertNotNull(trip.copy(id = Long.MAX_VALUE).problem())
        assertNotNull(trip.copy(id = MAX_IMPORTED_ID + 1).problem())
        assertNull(trip.copy(id = MAX_IMPORTED_ID).problem())
    }

    @Test
    fun `a trip that is still being recorded, or has a status MilO does not know`() {
        val open = trip.copy(status = "OPEN").problem()

        assertTrue(open, open?.contains("only a closed trip") == true)
        assertNotNull(trip.copy(status = "finished").problem())
        assertNotNull(trip.copy(status = "").problem())
    }

    @Test
    fun `a trip started by, or saved as, something MilO does not know`() {
        assertNotNull(trip.copy(startedBy = "COMPANION").problem())
        assertNotNull(trip.copy(category = "WORK").problem())
        assertNull(trip.copy(category = null).problem())
    }

    @Test
    fun `a time before 1970`() {
        assertNotNull(trip.copy(startedAtMs = -1).problem())
        assertNotNull(trip.copy(endedAtMs = -1).problem())
        assertNotNull(trip.copy(recordedStartedAtMs = -1).problem())
        assertNotNull(trip.copy(recordedEndedAtMs = -1).problem())
        assertNotNull(trip.copy(addressLastAttemptAtMs = -1).problem())
        assertNull(trip.copy(startedAtMs = 0, endedAtMs = 0).problem())
    }

    @Test
    fun `a distance that is negative`() {
        assertNotNull(trip.copy(distanceMetres = -0.1).problem())
        assertNotNull(trip.copy(recordedDistanceMetres = -1.0).problem())
        assertNotNull(trip.copy(distanceMetres = Double.NaN).problem())
        assertNull(trip.copy(distanceMetres = 0.0).problem())
    }

    @Test
    fun `a position off the earth, or only half there`() {
        assertNotNull(trip.copy(startLatitude = 90.01).problem())
        assertNotNull(trip.copy(endLongitude = -180.5).problem())
        assertNotNull(trip.copy(startLatitude = null).problem())
        assertNotNull(trip.copy(endLongitude = null).problem())
        assertNull(trip.copy(startLatitude = null, startLongitude = null).problem())
        assertNull(trip.copy(startLatitude = -90.0, startLongitude = 180.0).problem())
    }

    @Test
    fun `a negative number of address lookups`() {
        assertNotNull(trip.copy(addressAttempts = -1).problem())
    }

    @Test
    fun `a sent report with an id, a kind or days that cannot be`() {
        assertNotNull(report.copy(id = 0).problem())
        assertNotNull(report.copy(kind = "WEEK").problem())
        assertNotNull(report.copy(firstDay = "2026-09-31").problem())
        assertNotNull(report.copy(lastDay = "September").problem())
        assertNotNull(report.copy(firstDay = "2026-10-01", lastDay = "2026-09-30").problem())
    }

    @Test
    fun `a report for a month has to cover that month and no other days`() {
        assertNotNull(report.copy(lastDay = "2026-09-29").problem())
        assertNotNull(report.copy(firstDay = "2026-09-02").problem())
        assertNotNull(report.copy(lastDay = "2026-10-31").problem())
        // The same days as a date range are a date range.
        assertNull(report.copy(kind = "RANGE", lastDay = "2026-09-29").problem())
        assertNull(report.copy(firstDay = "2028-02-01", lastDay = "2028-02-29").problem())
    }

    @Test
    fun `a sent report with a negative figure`() {
        assertNotNull(report.copy(sentAtMs = -1).problem())
        assertNotNull(report.copy(tripCount = -1).problem())
        assertNotNull(report.copy(revision = -1).problem())
        assertNotNull(report.copy(distanceMetres = -1.0).problem())
    }

    @Test
    fun `settings that no setter of the settings store would take`() {
        assertNotNull(settings.copy(gracePeriodSeconds = -1).problem())
        assertNotNull(settings.copy(minimumTripDistanceMetres = -1).problem())
        assertNotNull(settings.copy(reminderDay = 0).problem())
        assertNotNull(settings.copy(reminderDay = 32).problem())
        assertNotNull(settings.copy(reportName = " Sam ").problem())
        assertNotNull(settings.copy(reportCompany = "").problem())
        assertNotNull(settings.copy(reportVehicle = "x".repeat(81)).problem())
        assertNotNull(settings.copy(accountantEmail = "accounts@example").problem())
        assertNull(settings.copy(reportName = null, accountantEmail = null).problem())
    }

    @Test
    fun `a truck whose address is not a Bluetooth address`() {
        val truck = checkNotNull(settings.truck)

        assertNotNull(settings.copy(truck = truck.copy(address = "aa:bb:cc:dd:ee:ff")).problem())
        assertNotNull(settings.copy(truck = truck.copy(address = "AA:BB:CC:DD:EE")).problem())
        assertNotNull(settings.copy(truck = truck.copy(address = "the truck")).problem())
        assertNotNull(settings.copy(truck = truck.copy(name = " ")).problem())
        assertNull(settings.copy(truck = truck.copy(name = null)).problem())
        assertNull(settings.copy(truck = null).problem())
    }

    @Test
    fun `a work schedule that is not seven days with an end after each start`() {
        val days = settings.schedule
        val monday = days.first()

        assertNotNull(settings.copy(schedule = days.drop(1)).problem())
        assertNotNull(settings.copy(schedule = days.drop(1) + days.last()).problem())
        assertNotNull(
            settings.copy(schedule = days.drop(1) + monday.copy(day = "Monday")).problem(),
        )
        assertNotNull(
            settings.copy(schedule = days.drop(1) + monday.copy(endMinute = 1440)).problem(),
        )
        assertNotNull(
            settings.copy(schedule = days.drop(1) + monday.copy(startMinute = -1)).problem(),
        )
        val endsAsItStarts = monday.copy(startMinute = 600, endMinute = 600)
        assertNotNull(settings.copy(schedule = days.drop(1) + endsAsItStarts).problem())
        // In any order.
        assertNull(settings.copy(schedule = days.reversed()).problem())
    }

    @Test
    fun `a row of points is read as written`() {
        for (point in somePoints) {
            assertEquals(PointRow.Read(point.copy(id = 0)), pointOfRow(point.toExportRow()))
        }
    }

    @Test
    fun `a row of points that is not seven numbers`() {
        val bad =
            listOf(
                "[1,2,3,53.5,-113.5,4.5]",
                "[1,2,3,53.5,-113.5,4.5,1.0,9]",
                "{\"tripId\":1}",
                "[1,2,3,\"53.5\",-113.5,4.5,1.0]",
                "[1,2,3,53.5,-113.5,[4.5],1.0]",
                "[1,2,3,53.5,-113.5,4.5,true]",
                "[1,2,3,53.5,-113.5,4.5",
                "12",
            )

        for (row in bad) assertTrue(row, pointOfRow(row) is PointRow.Bad)
    }

    @Test
    fun `a row of points with a value that cannot be`() {
        val bad =
            listOf(
                "[0,2,3,53.5,-113.5,4.5,1.0]",
                "[1.5,2,3,53.5,-113.5,4.5,1.0]",
                "[1,-2,3,53.5,-113.5,4.5,1.0]",
                "[1,2,-3,53.5,-113.5,4.5,1.0]",
                "[1,2,3,null,-113.5,4.5,1.0]",
                "[1,2,3,91,-113.5,4.5,1.0]",
                "[1,2,3,53.5,181,4.5,1.0]",
                "[1,2,3,NaN,-113.5,4.5,1.0]",
                "[1,2,3,53.5,-113.5,1e99,1.0]",
                "[1,2,3,53.5,-113.5,4.5,abc]",
                "[null,2,3,53.5,-113.5,4.5,1.0]",
            )

        for (row in bad) assertTrue(row, pointOfRow(row) is PointRow.Bad)
        assertTrue(pointOfRow("[1,2,3,53.5,-113.5,null,null]") is PointRow.Read)
        assertTrue(pointOfRow(" [ 1 , 2 , 3 , 53 , -113 , 4 , 1 ] ") is PointRow.Read)
    }
}
