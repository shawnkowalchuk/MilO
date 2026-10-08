package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.reportDays
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.formatDistance
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.core.util.tenthsOf
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.report.changedSinceSent
import com.shawnkowalchuk.milo.data.report.monthSubmission
import com.shawnkowalchuk.milo.data.report.selectForReport
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.data.trip.todayTrips
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * One rule for totals: the figures of the Trips screen's month card and of each day's heading,
 * and Today's, are the figures the report for the accountant prints for the same trips. The
 * test month is made to round badly, so that adding up metres and rounding once would give
 * another total.
 */
class TripMonthReportTotalsTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val october = YearMonth.of(2026, 10)
    private var nextId = 1L

    private fun trip(
        start: String,
        metres: Double,
        category: TripCategory? = TripCategory.BUSINESS,
        status: TripStatus = TripStatus.FINISHED,
    ): Trip {
        val startedAtMs = LocalDateTime.parse(start).atZone(edmonton).toInstant().toEpochMilli()
        return Trip(
            id = nextId++,
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + 20 * MINUTE_MS,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = metres,
            category = category,
        )
    }

    /** Sixty Business trips over three days that each lose or gain metres to the rounding. */
    private val month: List<Trip> =
        (0 until 60).map { i ->
            val day = 5 + i % 3
            val minute = (i / 3).toString().padStart(2, '0')
            trip("2026-10-0${day}T08:$minute", metres = 11_504.0 + (i % 4) * 30)
        } +
            listOf(
                trip("2026-10-05T12:00", 22_222.0, TripCategory.PERSONAL),
                trip("2026-10-06T12:00", 9_999.0, category = null),
                trip("2026-10-07T12:00", 44_444.0, status = TripStatus.DELETED),
                trip("2026-10-07T13:00", 150.0, status = TripStatus.DISCARDED),
            )

    private fun summary(trips: List<Trip> = month) = monthSummary(
        trips = trips,
        zone = edmonton,
        showLeftOut = false,
        liveTripId = null,
        liveDistanceMetres = null,
        liveStart = null,
        unit = DistanceUnit.KILOMETRES,
    )

    private fun reportOf(trips: List<Trip> = month) =
        selectForReport(trips, ReportPeriod.Month(october), edmonton)

    @Test
    fun `the month card's Business figure is the report's number of trips and total`() {
        val business = summary().totals.business
        val onReport = reportOf().trips

        assertEquals(60, business.count)
        assertEquals(onReport.size, business.count)
        assertEquals(onReport.sumOf { it.tenths(DistanceUnit.KILOMETRES) }, business.tenths)
        // And it is not what the metres would give, rounded once: the two rules do differ here.
        val metresOnce =
            formatDistance(
                onReport.sumOf {
                    it.distanceMetres
                },
                DistanceUnit.KILOMETRES,
                Locale.ROOT,
            )
        assertNotEquals(metresOnce, formatTenths(business.tenths, Locale.ROOT))
    }

    @Test
    fun `each day's heading has the subtotal the report prints for that day`() {
        val days = summary().days.associate { it.date to it.businessTenths }
        val reportDays = reportDays(reportOf().trips, edmonton)

        assertEquals(3, reportDays.size)
        for (day in reportDays) {
            assertEquals(day.tenths(DistanceUnit.KILOMETRES), days[day.date])
        }
        // The days add up to the month, on the screen as on the report.
        assertEquals(summary().totals.business.tenths, days.values.sum())
    }

    @Test
    fun `the rows of a day add up to its heading, and the headings to the month`() {
        val month = summary()

        for (day in month.days) {
            val rows =
                day.trips
                    .filter { it.category == TripCategory.BUSINESS }
                    .sumOf {
                        formatDistance(
                            it.distanceMetres ?: 0.0,
                            DistanceUnit.KILOMETRES,
                            Locale.ROOT,
                        ).toBigDecimal()
                    }
            assertEquals(rows.toPlainString(), formatTenths(day.businessTenths, Locale.ROOT))
        }
    }

    @Test
    fun `Today on Home and on the Android Auto screen add up by the same rule`() {
        val firstDay = summary().days.last().date
        val firstDayEnds = firstDay.plusDays(1).atStartOfDay(edmonton).toInstant().toEpochMilli()
        val oneDay = month.filter { it.startedAtMs < firstDayEnds }
        val today = todayTrips(oneDay)
        val report = reportOf(oneDay)

        assertEquals(
            report.trips.sumOf { it.tenths(DistanceUnit.KILOMETRES) },
            today.totals(DistanceUnit.KILOMETRES).business.tenths,
        )
        // The car's one figure is every counted trip of the day, each as it is printed.
        val parts = today.totals(DistanceUnit.KILOMETRES)
        assertEquals(
            parts.business.tenths + parts.personal.tenths + parts.unsorted.tenths,
            today.totalTenths(DistanceUnit.KILOMETRES),
        )
    }

    // ---- The same with miles chosen (2026-10-07) -------------------------------------------------

    private fun summaryIn(unit: DistanceUnit, trips: List<Trip> = month) = monthSummary(
        trips = trips,
        zone = edmonton,
        showLeftOut = false,
        liveTripId = null,
        liveDistanceMetres = null,
        liveStart = null,
        unit = unit,
    )

    @Test
    fun `in miles the month, each day and each row add up as the report in miles adds up`() {
        val miles = DistanceUnit.MILES
        val inMiles = summaryIn(miles)
        val onReport = reportOf()

        assertEquals(miles, inMiles.unit)
        // The month card's figure is the total a report made in miles prints.
        assertEquals(60, inMiles.totals.business.count)
        assertEquals(onReport.totalTenths(miles), inMiles.totals.business.tenths)
        assertEquals(4_305L, inMiles.totals.business.tenths)
        // Each day's heading is that report's subtotal, and the days add up to the month.
        val days = inMiles.days.associate { it.date to it.businessTenths }
        for (day in reportDays(onReport.trips, edmonton)) {
            assertEquals(day.tenths(miles), days[day.date])
        }
        assertEquals(inMiles.totals.business.tenths, days.values.sum())
        // And the Business rows of a day, each written from its metres, add up to its heading.
        for (day in inMiles.days) {
            val rows =
                day.trips
                    .filter { it.kind == TripKind.COUNTED && it.category == TripCategory.BUSINESS }
                    .sumOf { tenthsOf(it.distanceMetres ?: 0.0, miles) }

            assertEquals("${day.date}", day.businessTenths, rows)
        }
        // Personal and unsorted are kept apart in miles as they are in kilometres.
        assertEquals(Tally(1, 138), inMiles.totals.personal)
        assertEquals(Tally(1, 62), inMiles.totals.unsorted)
    }

    @Test
    fun `the month in kilometres is the month it was before there was a choice`() {
        assertEquals(summary(), summaryIn(DistanceUnit.KILOMETRES))
        assertEquals(6_930L, summary().totals.business.tenths)
        assertEquals(DistanceUnit.KILOMETRES, summary().unit)
    }

    @Test
    fun `the miles on the month card are not the kilometres converted`() {
        // 693.0 km would be 430.6 mi. The sixty trips, each as printed in miles, are 430.5.
        assertEquals(4_306L, tenthsOf(693_000.0, DistanceUnit.MILES))
        assertEquals(4_305L, summaryIn(DistanceUnit.MILES).totals.business.tenths)
    }

    // ---- Changed since it was sent, on the month card --------------------------------------------

    /** The row "I sent it" stores for a report of [trips]: its count and its printed total. */
    private fun sentReportOf(trips: List<Trip>): SentReport {
        val listed = reportOf(trips).trips
        return SentReport(
            id = 1,
            kind = SentReportKind.MONTH,
            firstDay = october.atDay(1).toEpochDay(),
            lastDay = october.atEndOfMonth().toEpochDay(),
            sentAtMs = 1_791_300_000_000L,
            tripCount = listed.size,
            distanceMetres =
                metresOfTenths(
                    listed.sumOf { it.tenths(DistanceUnit.KILOMETRES) },
                    DistanceUnit.KILOMETRES,
                ),
            revision = 0,
        )
    }

    private fun changed(sent: SentReport, now: List<Trip>) = summary(now).totals.business.let {
        changedSinceSent(monthSubmission(october, listOf(sent)), it.count) { _ -> it.tenths }
    }

    /** The row "I sent it" stores for a report of [trips] printed in [unit]. */
    private fun sentReportIn(unit: DistanceUnit, trips: List<Trip> = month): SentReport =
        sentReportOf(trips).copy(
            distanceMetres = metresOfTenths(reportOf(trips).totalTenths(unit), unit),
            distanceUnit = unit,
        )

    /** What the Trips screen works out for the month card, with [shownIn] chosen in Settings. */
    private fun changedOnScreen(sent: SentReport, now: List<Trip>, shownIn: DistanceUnit) =
        summaryIn(shownIn, now).totals.business.let { business ->
            changedSinceSent(monthSubmission(october, listOf(sent)), business.count) { sentIn ->
                categoryTotals(now, sentIn).business.tenths
            }
        }

    @Test
    fun `choosing the other unit never says a sent month has changed, whichever it was sent in`() {
        for (printedIn in DistanceUnit.entries) {
            for (shownIn in DistanceUnit.entries) {
                assertNull(
                    "printed in $printedIn, shown in $shownIn",
                    changedOnScreen(sentReportIn(printedIn), month, shownIn),
                )
            }
        }
    }

    @Test
    fun `a real change is still noticed after the unit was changed, in the report's own unit`() {
        val longer = month.map {
            if (it.id ==
                month.first().id
            ) {
                it.copy(distanceMetres = 12_000.0)
            } else {
                it
            }
        }

        for (printedIn in DistanceUnit.entries) {
            for (shownIn in DistanceUnit.entries) {
                val changed = changedOnScreen(sentReportIn(printedIn), longer, shownIn)

                assertEquals("printed in $printedIn, shown in $shownIn", printedIn, changed?.unit)
                assertEquals(60, changed?.tripCount)
                assertEquals(
                    reportOf(longer).totalTenths(printedIn),
                    changed?.tenths,
                )
                assertEquals(reportOf(month).totalTenths(printedIn), changed?.sentTenths)
            }
        }
    }

    @Test
    fun `a month whose trips are as they were sent says nothing, however badly they round`() {
        assertNull(changed(sentReportOf(month), month))
    }

    @Test
    fun `a trip added, deleted, marked Personal or made longer after sending is noticed`() {
        val sent = sentReportOf(month)
        val first = month.first()

        val added = month + trip("2026-10-20T09:00", 5_000.0)
        val deleted = month.map {
            if (it.id ==
                first.id
            ) {
                it.copy(status = TripStatus.DELETED)
            } else {
                it
            }
        }
        val marked =
            month.map { if (it.id == first.id) it.copy(category = TripCategory.PERSONAL) else it }
        val longer = month.map { if (it.id == first.id) it.copy(distanceMetres = 12_000.0) else it }

        assertEquals(61, changed(sent, added)?.tripCount)
        assertEquals(59, changed(sent, deleted)?.tripCount)
        assertEquals(59, changed(sent, marked)?.tripCount)
        val nowLonger = changed(sent, longer)
        assertNotNull(nowLonger)
        assertEquals(60, nowLonger?.tripCount)
        assertEquals(nowLonger!!.sentTenths + 5, nowLonger.tenths)
    }

    @Test
    fun `a Personal trip that changes, and a time or an address, are not noticed`() {
        val sent = sentReportOf(month)
        val personal = month.first { it.category == TripCategory.PERSONAL }
        val business = month.first()

        val personalLonger =
            month.map { if (it.id == personal.id) it.copy(distanceMetres = 99_000.0) else it }
        val addressTyped =
            month.map { if (it.id == business.id) it.copy(startAddress = "Shop") else it }
        // Too small to change the printed figure: 11,504 m and 11,540 m are both 11.5 km.
        val withinATenth =
            month.map { if (it.id == business.id) it.copy(distanceMetres = 11_540.0) else it }

        assertNull(changed(sent, personalLonger))
        assertNull(changed(sent, addressTyped))
        assertNull(changed(sent, withinATenth))
    }

    @Test
    fun `the days of the test month are the three it was built for`() {
        assertEquals(
            listOf(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 5)),
            summary().days.map { it.date },
        )
    }
}
