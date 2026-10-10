package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.DAY_MS
import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.FilingRules
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.classifyTrip
import com.shawnkowalchuk.milo.core.schedule.fileTrip
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.data.report.selectForReport
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.feature.tripedit.FormCheck
import com.shawnkowalchuk.milo.feature.tripedit.FormProblem
import com.shawnkowalchuk.milo.feature.tripedit.formFor
import com.shawnkowalchuk.milo.feature.tripedit.formProblems
import com.shawnkowalchuk.milo.feature.trips.monthOfSavedTrip
import com.shawnkowalchuk.milo.platform.nothingrecorded.countsAsRecordedToday
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS

/**
 * What the screens, the report and the work schedule make of a trip whose START or END falls
 * into the seconds in which the phone's date is one day ahead (ADR-005). The pure functions
 * every screen counts with are not changed. These were the investigation's proofs (2026-10-07)
 * of what follows downstream from a start or an end stamped a day ahead: the trip in the next
 * month's report, sorted by tomorrow's weekday, flagged as past the schedule. Each stamp is now
 * taken from MilO's clock in the middle of such a jump ([JumpingPhone]), and each test shows
 * the trip where it belongs.
 */
class ScreensClockJumpTest {
    private val zone = ZoneId.of("America/Edmonton")

    /** The unit the edit screen's form shows a distance in. No test here looks at a distance. */
    private val unit = DistanceUnit.KILOMETRES

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** What MilO's clock reads in a jump of the phone's date that begins at the true [atMs]. */
    private fun timeInsideAJumpAt(atMs: Long): Long {
        val phone = JumpingPhone(atMs - 4 * SECOND_MS)
        phone.clock.now()
        phone.setAhead()
        phone.advance(4 * SECOND_MS)
        return phone.clock.now()
    }

    private fun businessTrip(startedAtMs: Long, endedAtMs: Long) = Trip(
        id = 21,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = 18_400.0,
        category = TripCategory.BUSINESS,
    )

    @Test
    fun `a trip that starts in the seconds ahead on a month's last day is in that month`() {
        // Monday 30 November 2026, 15:10, while the phone reads Tuesday 1 December.
        val realStart = at("2026-11-30T15:10")
        val start = timeInsideAJumpAt(realStart)
        val trip = businessTrip(start, start + 20 * MINUTE_MS)
        val november = YearMonth.of(2026, 11)
        val december = YearMonth.of(2026, 12)

        assertEquals(realStart, start)
        fun listedIn(month: YearMonth) =
            selectForReport(listOf(trip), ReportPeriod.Month(month), zone).trips.size
        assertEquals(1, listedIn(november))
        assertEquals(0, listedIn(december))

        // Trips shows it in November, the month that is on screen. Before the fix its month
        // was December, which cannot be stepped to before it begins: stored, and not to be
        // seen until midnight.
        assertEquals(november, monthOf(trip.startedAtMs, zone))
        assertEquals(november, monthOfSavedTrip(trip.startedAtMs, zone, current = november))

        // Home's "today" and the nothing-recorded check, on the real day: one of today's.
        val today = daySpan(localDateOf(realStart, zone), zone)
        assertTrue(trip.startedAtMs >= today.fromMs && trip.startedAtMs < today.untilMs)
        assertTrue(countsAsRecordedToday(trip, today))
    }

    @Test
    fun `a trip that starts in the seconds ahead is sorted by the weekday it is`() {
        val friday = timeInsideAJumpAt(at("2026-10-09T15:10"))
        val sunday = timeInsideAJumpAt(at("2026-10-11T15:10"))

        // Friday afternoon is work. Before the fix it was read as Saturday: Personal, and
        // with "Ignore them" set for trips outside the schedule, stored as discarded.
        assertEquals(
            TripCategory.BUSINESS,
            classifyTrip(friday, friday + 20 * MINUTE_MS, DEFAULT_WORK_SCHEDULE, zone).category,
        )
        val ignoring = FilingRules(DEFAULT_WORK_SCHEDULE, ignoreOutsideSchedule = true)
        assertFalse(fileTrip(friday, friday + 20 * MINUTE_MS, kept = true, ignoring, zone).ignored)

        // Sunday afternoon is not work. Before the fix it was read as Monday: Business, and
        // on the report.
        assertEquals(
            TripCategory.PERSONAL,
            classifyTrip(sunday, sunday + 20 * MINUTE_MS, DEFAULT_WORK_SCHEDULE, zone).category,
        )
    }

    @Test
    fun `a trip that ends in the seconds ahead is fifteen minutes long, inside its work day`() {
        // Started Wednesday 16:10; ended 16:25, while the phone read Thursday 16:25.
        val start = at("2026-10-07T16:10")
        val end = timeInsideAJumpAt(start + 15 * MINUTE_MS)

        val filed = classifyTrip(start, end, DEFAULT_WORK_SCHEDULE, zone)

        // Before the fix: flagged as running past the schedule, and on October's report as a
        // trip of 24 hours and 15 minutes.
        assertEquals(TripCategory.BUSINESS, filed.category)
        assertFalse(filed.ranPastSchedule)
        val trip = businessTrip(start, end)
        val listed = selectForReport(listOf(trip), ReportPeriod.Month(YearMonth.of(2026, 10)), zone)
        val minutes = (checkNotNull(listed.trips.single().endedAtMs) - start) / MINUTE_MS
        assertEquals(15L, minutes)
    }

    @Test
    fun `the edit screen has nothing to say about a trip that started in the seconds ahead`() {
        val start = timeInsideAJumpAt(at("2026-10-07T15:10"))
        val trip = businessTrip(start, start + 20 * MINUTE_MS)
        val check = FormCheck(nowMs = at("2026-10-07T19:00"), recordingSinceMs = null)

        assertEquals(
            emptyList<FormProblem>(),
            formProblems(formFor(trip, zone, unit), trip, check, zone),
        )
    }

    @Test
    fun `a trip the build before this one dated tomorrow is put right by setting its day back`() {
        // Kept as the investigation wrote it: a trip that an earlier build stored with
        // tomorrow's date is not changed by ADR-005, and this is the way out it always had.
        val realStart = at("2026-10-07T15:10")
        val nowMs = at("2026-10-07T19:00")
        val trip = businessTrip(realStart + DAY_MS, realStart + DAY_MS + 20 * MINUTE_MS)
        val check = FormCheck(nowMs, recordingSinceMs = null)

        val asStored = formFor(trip, zone, unit)
        assertEquals(listOf(FormProblem.IN_THE_FUTURE), formProblems(asStored, trip, check, zone))

        val putRight = asStored.copy(date = localDateOf(nowMs, zone))
        assertEquals(emptyList<FormProblem>(), formProblems(putRight, trip, check, zone))
    }
}
