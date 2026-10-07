package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.address.TripPlace
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When Home shows the tile for a report that has not been sent, and how the screen is put
 * together from its parts.
 */
class HomeUiTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val september = YearMonth.of(2026, 9)
    private val october6 = LocalDate.of(2026, 10, 6)

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(edmonton).toInstant().toEpochMilli()

    private fun trip(start: String, category: TripCategory = TripCategory.BUSINESS): Trip {
        val startedAtMs = at(start)
        return Trip(
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + 1_200_000,
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = 12_300.0,
            category = category,
        )
    }

    private val septemberTrips = listOf(trip("2026-09-14T08:00"), trip("2026-09-15T08:00"))

    private fun sentFor(month: YearMonth): SentReport {
        val period = ReportPeriod.Month(month)
        return SentReport(
            kind = SentReportKind.MONTH,
            firstDay = period.firstDay.toEpochDay(),
            lastDay = period.lastDay.toEpochDay(),
            sentAtMs = at("2026-10-02T09:00"),
            tripCount = 2,
            distanceMetres = 24_600.0,
            revision = 0,
        )
    }

    private fun waiting(
        now: String = "2026-10-06T10:00",
        stored: MiloSettings? = MiloSettings(),
        sent: List<SentReport> = emptyList(),
        trips: List<Trip> = septemberTrips,
    ): YearMonth? = reportWaiting(at(now), edmonton, stored, sent, trips)

    // ---- The report tile --------------------------------------------------------------------------

    @Test
    fun `last month's report is waiting once the month is over and it has not been sent`() {
        assertEquals(september, waiting())
    }

    @Test
    fun `a report that is recorded as sent is not waiting`() {
        assertNull(waiting(sent = listOf(sentFor(september))))
    }

    @Test
    fun `a report sent for another month does not answer for last month`() {
        assertEquals(september, waiting(sent = listOf(sentFor(YearMonth.of(2026, 8)))))
    }

    @Test
    fun `a month without a Business trip has nothing to send`() {
        assertNull(waiting(trips = emptyList()))
        assertNull(waiting(trips = listOf(trip("2026-09-14T08:00", TripCategory.PERSONAL))))
    }

    @Test
    fun `the tile follows the reminder's switch and its day, as the notification does`() {
        assertNull(waiting(stored = MiloSettings(reminderEnabled = false)))
        assertNull(waiting(stored = MiloSettings(reminderDay = 7)))
        assertEquals(september, waiting(stored = MiloSettings(reminderDay = 6)))
    }

    @Test
    fun `the tile stays after today's notification has been shown`() {
        val shownToday = MiloSettings(reminderShown = ReminderShown(september, october6))

        assertEquals(september, waiting(stored = shownToday))
    }

    @Test
    fun `without the settings nothing is shown, as the reminder shows nothing`() {
        assertNull(waiting(stored = null))
    }

    @Test
    fun `on the first of a month the tile is about the month that has just ended`() {
        assertEquals(september, waiting(now = "2026-10-01T00:05"))
        // In January it is December of the year before.
        val december = listOf(trip("2025-12-10T08:00"))
        assertEquals(YearMonth.of(2025, 12), waiting(now = "2026-01-01T09:00", trips = december))
    }

    // ---- Putting the screen together --------------------------------------------------------------

    private val open =
        CurrentTrip(
            tripId = 7,
            startedAtMs = at("2026-10-06T14:14"),
            startedBy = TripStartCause.TRUCK,
            distanceMetres = 12_400.0,
            waitingForTruck = false,
        )

    private val now =
        HomeNow(
            date = october6,
            activity = TripActivity(truckConnected = false),
            setupNeedsAttention = false,
            stored = MiloSettings(truckAddress = "AA:BB", truckName = "F-150"),
        )

    private val read =
        HomeRead(
            figures = homeTrips(october6, edmonton, emptyList()),
            reportWaiting = september,
            openTripStart = null,
            nowMs = at("2026-10-06T14:32"),
        )

    @Test
    fun `the parts are passed on as they are`() {
        val ui = homeUi(now, read)

        assertEquals(october6, ui.date)
        assertEquals(read.figures, ui.figures)
        assertEquals(september, ui.reportWaiting)
        assertEquals(read.nowMs, ui.nowMs)
        assertEquals(TruckTileState(TruckState.NOT_CONNECTED, "F-150"), ui.truck)
        assertNull(ui.startAddress)
    }

    @Test
    fun `trips that were read for another day are not shown under today's date`() {
        val yesterdays = read.copy(
            figures = homeTrips(october6.minusDays(1), edmonton, emptyList()),
        )

        assertNull(homeUi(now, yesterdays).figures)
    }

    @Test
    fun `the truck's tile is decided from the same trip state the screen shows`() {
        val recording = now.copy(activity = TripActivity(open, truckConnected = true))
        val known = read.copy(
            openTripStart = OpenTripStart(7, 53.5, -113.5, TripPlace.Known("Shop")),
        )

        val ui = homeUi(recording, known)

        assertEquals(TruckState.CONNECTED_RECORDING, ui.truck.state)
        assertEquals("Shop", ui.startAddress)
    }

    @Test
    fun `before the settings are read the truck has no name and no state is claimed`() {
        val ui = homeUi(now.copy(stored = null), read)

        assertEquals(TruckTileState(TruckState.CHECKING, truckName = null), ui.truck)
    }
}
