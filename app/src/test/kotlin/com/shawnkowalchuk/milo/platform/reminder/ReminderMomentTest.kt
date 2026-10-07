package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the reminder's decision is made from, as it is gathered from storage: the notification
 * and the home screen's tile both go through this one function.
 */
class ReminderMomentTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val september = YearMonth.of(2026, 9)

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(edmonton).toInstant().toEpochMilli()

    private val now = at("2026-10-06T10:00")

    private fun trip(
        start: String,
        category: TripCategory? = TripCategory.BUSINESS,
        status: TripStatus = TripStatus.FINISHED,
    ) = Trip(
        startedAtMs = at(start),
        endedAtMs = at(start) + 1_200_000,
        status = status,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = 12_300.0,
        category = category,
    )

    private fun sent(period: ReportPeriod, kind: SentReportKind) = SentReport(
        kind = kind,
        firstDay = period.firstDay.toEpochDay(),
        lastDay = period.lastDay.toEpochDay(),
        sentAtMs = now,
        tripCount = 1,
        distanceMetres = 12_300.0,
        revision = 0,
    )

    @Test
    fun `the switch, the day and what was shown are the stored ones`() {
        val shown = ReminderShown(september, LocalDate.of(2026, 10, 5))
        val stored = MiloSettings(reminderEnabled = false, reminderDay = 12, reminderShown = shown)

        val moment = reminderMoment(now, edmonton, stored, emptyList(), emptyList())

        assertEquals(
            ReminderMoment(
                nowMs = now,
                zone = edmonton,
                enabled = false,
                reminderDay = 12,
                submitted = false,
                businessTrips = 0,
                shown = shown,
            ),
            moment,
        )
    }

    @Test
    fun `the trips counted are the ones last month's report would list`() {
        val trips =
            listOf(
                trip("2026-09-14T08:00"),
                trip("2026-09-30T23:50"),
                trip("2026-09-15T08:00", TripCategory.PERSONAL),
                trip("2026-09-16T08:00", category = null),
                trip("2026-09-17T08:00", status = TripStatus.DELETED),
                trip("2026-09-18T08:00", status = TripStatus.DISCARDED),
                // Not last month's, should the caller hand over more than the month.
                trip("2026-10-01T00:00"),
                trip("2026-08-31T23:59"),
            )

        val moment = reminderMoment(now, edmonton, MiloSettings(), emptyList(), trips)

        assertEquals(2, moment.businessTrips)
    }

    @Test
    fun `only a report for the whole of last month makes it submitted`() {
        val month = ReportPeriod.Month(september)
        val sameDays = ReportPeriod.Range(month.firstDay, month.lastDay)
        val august = ReportPeriod.Month(YearMonth.of(2026, 8))

        fun submitted(report: SentReport): Boolean =
            reminderMoment(now, edmonton, MiloSettings(), listOf(report), emptyList()).submitted

        assertEquals(true, submitted(sent(month, SentReportKind.MONTH)))
        assertEquals(false, submitted(sent(sameDays, SentReportKind.RANGE)))
        assertEquals(false, submitted(sent(august, SentReportKind.MONTH)))
    }
}
