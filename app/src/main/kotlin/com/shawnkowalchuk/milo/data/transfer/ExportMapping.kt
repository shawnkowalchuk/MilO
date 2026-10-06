package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.TransferredSettings
import com.shawnkowalchuk.milo.data.settings.TransferredTruck
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// Between what is stored and what the export file holds, in both directions. Pure, so the
// round trip is tested without a phone.
//
// Every stored constant is spelled out here and never taken from its own name in code: a
// constant that is renamed one day must still be read from a file written before.

private const val SECONDS_PER_MINUTE = 60

internal val STATUS_WORDS: Map<TripStatus, String> =
    mapOf(
        TripStatus.FINISHED to "FINISHED",
        TripStatus.DISCARDED to "DISCARDED",
        TripStatus.DELETED to "DELETED",
    )

internal val START_CAUSE_WORDS: Map<TripStartCause, String> =
    mapOf(TripStartCause.TRUCK to "TRUCK", TripStartCause.MANUAL to "MANUAL")

internal val CATEGORY_WORDS: Map<TripCategory, String> =
    mapOf(TripCategory.BUSINESS to "BUSINESS", TripCategory.PERSONAL to "PERSONAL")

internal val REPORT_KIND_WORDS: Map<SentReportKind, String> =
    mapOf(SentReportKind.MONTH to "MONTH", SentReportKind.RANGE to "RANGE")

internal val DAY_WORDS: Map<DayOfWeek, String> =
    mapOf(
        DayOfWeek.MONDAY to "monday",
        DayOfWeek.TUESDAY to "tuesday",
        DayOfWeek.WEDNESDAY to "wednesday",
        DayOfWeek.THURSDAY to "thursday",
        DayOfWeek.FRIDAY to "friday",
        DayOfWeek.SATURDAY to "saturday",
        DayOfWeek.SUNDAY to "sunday",
    )

/** The constant a word of the file stands for, or null for a word that stands for none. */
internal fun <T> Map<T, String>.constantFor(word: String?): T? =
    entries.firstOrNull { it.value == word }?.key

/**
 * A closed trip as the file holds it. The grace columns are not carried: they are empty on
 * every closed trip.
 *
 * @throws IllegalArgumentException for a trip that is still being recorded. It has no end and
 * no distance yet, and an export is not made while there is one.
 */
fun Trip.toExported(): ExportedTrip = ExportedTrip(
    id = id,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    status =
        requireNotNull(STATUS_WORDS[status]) { "Trip $id is $status and cannot be exported" },
    startedBy = START_CAUSE_WORDS.getValue(startedBy),
    truckSeen = truckSeen,
    distanceMetres = distanceMetres,
    startLatitude = startLatitude,
    startLongitude = startLongitude,
    endLatitude = endLatitude,
    endLongitude = endLongitude,
    startAddress = startAddress,
    endAddress = endAddress,
    addressAttempts = addressAttempts,
    addressLastAttemptAtMs = addressLastAttemptAtMs,
    category = category?.let(CATEGORY_WORDS::getValue),
    categorySetByHand = categorySetByHand,
    ranPastSchedule = ranPastSchedule,
    ignoredOutsideSchedule = ignoredOutsideSchedule,
    addedByHand = addedByHand,
    editedByHand = editedByHand,
    startAddressByHand = startAddressByHand,
    endAddressByHand = endAddressByHand,
    recordedStartedAtMs = recordedStartedAtMs,
    recordedEndedAtMs = recordedEndedAtMs,
    recordedDistanceMetres = recordedDistanceMetres,
)

/** The row to store for a trip of the file that [problem] found nothing wrong with. */
internal fun ExportedTrip.toTrip(): Trip = Trip(
    id = id,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    status = checkNotNull(STATUS_WORDS.constantFor(status)),
    startedBy = checkNotNull(START_CAUSE_WORDS.constantFor(startedBy)),
    truckSeen = truckSeen,
    distanceMetres = distanceMetres,
    startLatitude = startLatitude,
    startLongitude = startLongitude,
    endLatitude = endLatitude,
    endLongitude = endLongitude,
    startAddress = startAddress,
    endAddress = endAddress,
    addressAttempts = addressAttempts,
    addressLastAttemptAtMs = addressLastAttemptAtMs,
    category = category?.let { checkNotNull(CATEGORY_WORDS.constantFor(it)) },
    categorySetByHand = categorySetByHand,
    ranPastSchedule = ranPastSchedule,
    ignoredOutsideSchedule = ignoredOutsideSchedule,
    addedByHand = addedByHand,
    editedByHand = editedByHand,
    startAddressByHand = startAddressByHand,
    endAddressByHand = endAddressByHand,
    recordedStartedAtMs = recordedStartedAtMs,
    recordedEndedAtMs = recordedEndedAtMs,
    recordedDistanceMetres = recordedDistanceMetres,
)

fun SentReport.toExported(): ExportedSentReport = ExportedSentReport(
    id = id,
    kind = REPORT_KIND_WORDS.getValue(kind),
    firstDay = LocalDate.ofEpochDay(firstDay).toString(),
    lastDay = LocalDate.ofEpochDay(lastDay).toString(),
    sentAtMs = sentAtMs,
    tripCount = tripCount,
    distanceMetres = distanceMetres,
    revision = revision,
)

/** The row to store for a sent report of the file that [problem] found nothing wrong with. */
internal fun ExportedSentReport.toSentReport(): SentReport = SentReport(
    id = id,
    kind = checkNotNull(REPORT_KIND_WORDS.constantFor(kind)),
    firstDay = LocalDate.parse(firstDay).toEpochDay(),
    lastDay = LocalDate.parse(lastDay).toEpochDay(),
    sentAtMs = sentAtMs,
    tripCount = tripCount,
    distanceMetres = distanceMetres,
    revision = revision,
)

fun TransferredSettings.toExported(): ExportedSettings = ExportedSettings(
    truck = truck?.let { ExportedTruck(it.address, it.name) },
    gracePeriodSeconds = gracePeriodSeconds,
    minimumTripDistanceMetres = minimumTripDistanceMetres,
    soundEnabled = soundEnabled,
    schedule =
        DayOfWeek.entries.map { day ->
            val hours = schedule.on(day)
            ExportedDay(
                day = DAY_WORDS.getValue(day),
                tracked = hours.tracked,
                startMinute = hours.start.toSecondOfDay() / SECONDS_PER_MINUTE,
                endMinute = hours.end.toSecondOfDay() / SECONDS_PER_MINUTE,
            )
        },
    ignoreTripsOutsideSchedule = ignoreTripsOutsideSchedule,
    drivingAlertEnabled = drivingAlertEnabled,
    reportName = reportName,
    reportCompany = reportCompany,
    reportVehicle = reportVehicle,
    accountantEmail = accountantEmail,
    reminderEnabled = reminderEnabled,
    reminderDay = reminderDay,
)

/** The settings to store for the file's settings that [problem] found nothing wrong with. */
internal fun ExportedSettings.toTransferred(): TransferredSettings = TransferredSettings(
    truck = truck?.let { TransferredTruck(it.address, it.name) },
    gracePeriodSeconds = gracePeriodSeconds,
    minimumTripDistanceMetres = minimumTripDistanceMetres,
    soundEnabled = soundEnabled,
    schedule =
        WorkSchedule(
            schedule.associate { day ->
                val start = timeOfMinute(day.startMinute)
                val end = timeOfMinute(day.endMinute)
                checkNotNull(DAY_WORDS.constantFor(day.day)) to DayHours(day.tracked, start, end)
            },
        ),
    ignoreTripsOutsideSchedule = ignoreTripsOutsideSchedule,
    drivingAlertEnabled = drivingAlertEnabled,
    reportName = reportName,
    reportCompany = reportCompany,
    reportVehicle = reportVehicle,
    accountantEmail = accountantEmail,
    reminderEnabled = reminderEnabled,
    reminderDay = reminderDay,
)

internal fun timeOfMinute(minute: Int): LocalTime =
    LocalTime.ofSecondOfDay(minute.toLong() * SECONDS_PER_MINUTE)

/**
 * One raw GPS point as a row of the file: seven numbers in the order of
 * [EXPORT_POINT_COLUMNS]. The point's own id is not carried: the rows are written, and stored
 * again, in the order the fixes arrived, which is all the id says.
 *
 * An accuracy or a speed that is not a finite number is written as null, "the phone gave
 * none": JSON has no way to write such a number, and the trip rules never count a fix without
 * an accuracy.
 *
 * @throws IllegalStateException for a position that is not a finite number. Such a point
 * cannot be written, and leaving it out in silence would make the file less than it says.
 */
fun RawPoint.toExportRow(): String {
    check(latitude.isFinite() && longitude.isFinite()) {
        "Raw point $id of trip $tripId has a position that is not a number"
    }
    val accuracy = accuracyMetres?.takeIf { it.isFinite() }
    val speed = speedMetresPerSecond?.takeIf { it.isFinite() }
    return "[$tripId,$wallClockMs,$elapsedRealtimeMs,$latitude,$longitude,$accuracy,$speed]"
}
