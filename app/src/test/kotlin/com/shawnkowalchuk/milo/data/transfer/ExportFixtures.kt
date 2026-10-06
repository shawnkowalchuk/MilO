package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.settings.TransferredTruck
import com.shawnkowalchuk.milo.data.trip.Trip
import java.io.StringReader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

// What the tests of the export file share: one trip of every kind MilO can hold, the sent
// reports and settings that go with them, and the short ways to write and read a file.

internal val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

/** 2026-10-06 14:02:11 in Edmonton. */
internal const val EXPORTED_AT_MS = 1_791_316_931_000L

private const val MORNING_MS = 1_790_000_000_000L
private const val HOUR_MS = 3_600_000L

/** A trip the truck started and MilO recorded: positions, looked-up addresses, sorted by MilO. */
internal val recordedTrip =
    Trip(
        id = 1,
        startedAtMs = MORNING_MS,
        endedAtMs = MORNING_MS + HOUR_MS / 2,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = 18_412.75,
        startLatitude = 53.5461,
        startLongitude = -113.4937,
        endLatitude = 53.6316,
        endLongitude = -113.3239,
        startAddress = "12 Shop Rd, Edmonton",
        endAddress = "88 \"Quoted\" Ave, St. Albert",
        addressAttempts = 1,
        addressLastAttemptAtMs = MORNING_MS + HOUR_MS,
        category = TripCategory.BUSINESS,
        ranPastSchedule = true,
    )

/** A trip Shawn typed in: no positions, both addresses his, never open. */
internal val tripAddedByHand =
    Trip(
        id = 2,
        startedAtMs = MORNING_MS + 2 * HOUR_MS,
        endedAtMs = MORNING_MS + 3 * HOUR_MS,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.MANUAL,
        truckSeen = false,
        distanceMetres = 42_000.0,
        startAddress = "Yard",
        endAddress = null,
        category = TripCategory.BUSINESS,
        categorySetByHand = true,
        addedByHand = true,
        startAddressByHand = true,
        endAddressByHand = true,
    )

/** A recorded trip whose times, distance and one address were changed on the edit screen. */
internal val editedTrip =
    recordedTrip.copy(
        id = 3,
        startedAtMs = MORNING_MS + 4 * HOUR_MS,
        endedAtMs = MORNING_MS + 5 * HOUR_MS,
        distanceMetres = 25_000.0,
        endAddress = "Client's site\nGate 4",
        endAddressByHand = true,
        editedByHand = true,
        ranPastSchedule = false,
        recordedStartedAtMs = MORNING_MS + 4 * HOUR_MS + 61_000,
        recordedEndedAtMs = MORNING_MS + 5 * HOUR_MS - 7_000,
        recordedDistanceMetres = 24_381.4,
    )

/** A finished trip Shawn deleted on the Trips screen. */
internal val deletedTrip = recordedTrip.copy(id = 4, status = TripStatus.DELETED)

/** A trip under the minimum distance: no category from a hand, no address, a start by button. */
internal val discardedTrip =
    Trip(
        id = 5,
        startedAtMs = MORNING_MS + 6 * HOUR_MS,
        endedAtMs = MORNING_MS + 6 * HOUR_MS + 40_000,
        status = TripStatus.DISCARDED,
        startedBy = TripStartCause.MANUAL,
        truckSeen = false,
        distanceMetres = 0.0,
        category = TripCategory.PERSONAL,
    )

/** A trip left out because trips outside the work hours were set to be ignored. */
internal val ignoredTrip =
    recordedTrip.copy(
        id = 6,
        status = TripStatus.DISCARDED,
        category = TripCategory.PERSONAL,
        ranPastSchedule = false,
        ignoredOutsideSchedule = true,
    )

/** A trip Shawn marked Personal by hand on the Trips screen. */
internal val tripSortedByHand =
    recordedTrip.copy(id = 7, category = TripCategory.PERSONAL, categorySetByHand = true)

/**
 * A trip from before there was a schedule, never sorted, with no usable fix and no address
 * found after four lookups, and an end before its start: what a phone with a wrong clock
 * records, and what a file of MilO's own may therefore hold.
 */
internal val oddTrip =
    Trip(
        id = 9,
        startedAtMs = MORNING_MS + 8 * HOUR_MS,
        endedAtMs = MORNING_MS + 7 * HOUR_MS,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = false,
        distanceMetres = 1.0E-4,
        addressAttempts = 4,
        addressLastAttemptAtMs = MORNING_MS + 9 * HOUR_MS,
        category = null,
    )

/** One trip of every kind, in the order of their ids. Id 8 is missing: ids can have gaps. */
internal val everyKindOfTrip =
    listOf(
        recordedTrip,
        tripAddedByHand,
        editedTrip,
        deletedTrip,
        discardedTrip,
        ignoredTrip,
        tripSortedByHand,
        oddTrip,
    )

private fun day(text: String): Long = LocalDate.parse(text).toEpochDay()

/** A month sent twice, with the first revision removed since (a gap), and a date range. */
internal val sentReports =
    listOf(
        SentReport(
            1,
            SentReportKind.MONTH,
            day("2026-09-01"),
            day("2026-09-30"),
            100,
            61,
            716_400.0,
            0,
        ),
        SentReport(
            4,
            SentReportKind.MONTH,
            day("2026-09-01"),
            day("2026-09-30"),
            300,
            62,
            720_100.0,
            2,
        ),
        SentReport(5, SentReportKind.RANGE, day("2026-10-05"), day("2026-10-05"), 400, 0, 0.0, 0),
    )

/** Settings with nothing left at its default. */
internal val changedSettings =
    TransferredSettings(
        truck = TransferredTruck("AA:BB:CC:DD:EE:FF", "Work truck"),
        gracePeriodSeconds = 150,
        minimumTripDistanceMetres = 500,
        soundEnabled = false,
        schedule =
            DEFAULT_WORK_SCHEDULE
                .withTracked(DayOfWeek.SATURDAY, true)
                .with(DayOfWeek.MONDAY, DayHours(true, LocalTime.of(7, 0), LocalTime.of(23, 59))),
        ignoreTripsOutsideSchedule = true,
        drivingAlertEnabled = false,
        reportName = "Sam Driver",
        reportCompany = "Driver & Sons \"Ltd\"",
        reportVehicle = "Ford F-150, plate ABC-123",
        accountantEmail = "accounts@example.ca",
        reminderEnabled = false,
        reminderDay = 31,
    )

/** Fixes of three of the trips, one of them without an accuracy or a speed. */
internal val somePoints =
    listOf(
        RawPoint(11, 1, MORNING_MS + 5_000, 90_000, 53.5461, -113.4937, 4.5f, 12.25f),
        RawPoint(12, 1, MORNING_MS + 10_000, 95_000, 53.54612345678, -113.49371234567, 3.9f, 0f),
        RawPoint(13, 3, MORNING_MS + 4 * HOUR_MS, 120_000, 53.6, -113.3, null, null),
        RawPoint(14, 5, MORNING_MS + 6 * HOUR_MS, 7, -33.8688, 151.2093, 1.0E-4f, 3.4028235E38f),
    )

/** What an export of everything above is made of. */
internal fun sourceOf(
    trips: List<Trip> = everyKindOfTrip,
    reports: List<SentReport> = sentReports,
    settings: TransferredSettings? = changedSettings,
    points: List<RawPoint>? = somePoints,
    pointCount: Long? = points?.size?.toLong(),
): ExportSource = ExportSource(
    exportedAtMs = EXPORTED_AT_MS,
    zone = EDMONTON,
    appVersion = "0.1.0",
    databaseVersion = 5,
    settings = settings,
    sentReports = reports,
    trips = trips,
    pointCount = pointCount,
    // In two pages, so that what joins one page to the next is written too.
    points = { each -> points.orEmpty().chunked(3).forEach { each(it) } },
)

/** The file an export of [source] is. */
internal fun fileOf(source: ExportSource = sourceOf()): String = runBlocking {
    StringBuilder().also { writeExport(it, source) }.toString()
}

/** Reads [text] as a file, and collects the points it hands over. */
internal fun read(text: String, points: MutableList<RawPoint>? = null): ExportReading =
    runBlocking {
        readExport(open = { StringReader(text) }, onPoints = points?.let { { page -> it += page } })
    }

/** The points as an import stores them: without the ids they had on the phone they came from. */
internal fun List<RawPoint>.withoutIds(): List<RawPoint> = map { it.copy(id = 0) }
