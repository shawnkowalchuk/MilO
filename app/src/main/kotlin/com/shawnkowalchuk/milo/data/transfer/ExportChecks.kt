package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.schedule.areValidHours
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.REMINDER_DAYS
import com.shawnkowalchuk.milo.data.settings.isEmailAddress
import com.shawnkowalchuk.milo.data.settings.isReportText
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull

// What an import checks of each thing in the file before anything on the phone is touched.
// Pure, so every rule is tested without a phone.
//
// The rules ask whether a value could be what MilO stores, not whether it is plausible: a trip
// that ends before it starts is taken, because a phone with a wrong clock records exactly that
// (FINDINGS_LOG, "Deadlines and the trip cut-off are times of day"), and a file MilO wrote
// itself must never be one MilO refuses.

/**
 * The highest id a trip or a sent report of the file may have. The tables hand out ids upwards
 * from the highest one they have ever held, so one absurd id in a file would leave no number
 * for the next trip the truck starts. Two thousand million is far beyond any id MilO gives.
 */
const val MAX_IMPORTED_ID = 2_000_000_000L

private const val MINUTES_PER_DAY = 24 * 60
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0
private const val POINT_COLUMN_COUNT = 7

private val BLUETOOTH_ADDRESS = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")

private fun isTime(ms: Long?): Boolean = ms == null || ms >= 0

private fun isDistance(metres: Double?): Boolean =
    metres == null || (metres.isFinite() && metres >= 0)

/** A position is both numbers or neither, each within what a latitude and a longitude can be. */
private fun isPosition(latitude: Double?, longitude: Double?): Boolean =
    if (latitude == null || longitude == null) {
        latitude == null && longitude == null
    } else {
        latitude in -MAX_LATITUDE..MAX_LATITUDE && longitude in -MAX_LONGITUDE..MAX_LONGITUDE
    }

/** What is wrong with a trip of the file, in words that finish "Trip 12: …", or null. */
internal fun ExportedTrip.problem(): String? = when {
    id !in 1..MAX_IMPORTED_ID -> "its id is not a number a trip can have"

    STATUS_WORDS.constantFor(status) == null ->
        "its status is \"$status\", and only a closed trip can be in an export"

    START_CAUSE_WORDS.constantFor(startedBy) == null -> "startedBy is \"$startedBy\""

    category != null && CATEGORY_WORDS.constantFor(category) == null ->
        "its category is \"$category\""

    listOf(
        startedAtMs,
        endedAtMs,
        recordedStartedAtMs,
        recordedEndedAtMs,
        addressLastAttemptAtMs,
    ).any { !isTime(it) } -> "a time of it is before 1970"

    !isDistance(distanceMetres) || !isDistance(recordedDistanceMetres) ->
        "a distance of it is not a distance"

    !isPosition(startLatitude, startLongitude) || !isPosition(endLatitude, endLongitude) ->
        "a position of it is not a place on the earth, or is only half there"

    addressAttempts < 0 -> "its number of address lookups is negative"

    else -> null
}

/**
 * The same for a sent report: in words that finish "Sent report 3: …", or null.
 *
 * @param formatVersion the version of the form the file says it is in; the version this MilO
 * writes unless the reader says otherwise. From format 2 on a report must say which unit it
 * was printed in; in a file of format 1 none does, and one that does was not written by MilO.
 */
internal fun ExportedSentReport.problem(formatVersion: Int = EXPORT_FORMAT_VERSION): String? {
    val reportKind = REPORT_KIND_WORDS.constantFor(kind)
    val first = dayOrNull(firstDay)
    val last = dayOrNull(lastDay)
    return when {
        id !in 1..MAX_IMPORTED_ID -> "its id is not a number a report can have"

        reportKind == null -> "its kind is \"$kind\""

        first == null || last == null -> "\"$firstDay\" to \"$lastDay\" are not two calendar days"

        last.isBefore(first) -> "it ends ($lastDay) before it starts ($firstDay)"

        reportKind == SentReportKind.MONTH && !isWholeMonth(first, last) ->
            "it is a report for a month, and $firstDay to $lastDay is not a whole month"

        sentAtMs < 0 -> "it was sent before 1970"

        tripCount < 0 || revision < 0 -> "its number of trips or its revision is negative"

        !isDistance(distanceMetres) -> "its total is not a distance"

        formatVersion < FORMAT_WITH_REPORT_UNIT && distanceUnit != null ->
            "it names a unit, which a file of format $formatVersion does not hold"

        formatVersion >= FORMAT_WITH_REPORT_UNIT && distanceUnit == null ->
            "it does not say which unit its total was printed in"

        distanceUnit != null && REPORT_UNIT_WORDS.constantFor(distanceUnit) == null ->
            "its unit is \"$distanceUnit\""

        else -> null
    }
}

private fun isWholeMonth(first: LocalDate, last: LocalDate): Boolean {
    val month = YearMonth.from(first)
    return first == month.atDay(1) && last == month.atEndOfMonth()
}

private fun dayOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (notADay: DateTimeException) {
    null
}

/** The same for the settings: in words that finish "The settings: …", or null. */
internal fun ExportedSettings.problem(): String? = when {
    gracePeriodSeconds < 0 || minimumTripDistanceMetres < 0 ->
        "the grace period or the shortest trip is negative"

    reminderDay !in REMINDER_DAYS -> "the reminder's day ($reminderDay) is no day of a month"

    listOf(reportName, reportCompany, reportVehicle).any { it != null && !isReportText(it) } ->
        "the name, the company or the vehicle is not a text the report can print"

    accountantEmail != null && !isEmailAddress(accountantEmail) ->
        "the accountant's address is not an email address"

    truck != null && !truck.isATruck() ->
        "the truck's address is not a Bluetooth address, or its name is blank"

    else -> scheduleProblem()
}

private fun ExportedTruck.isATruck(): Boolean =
    BLUETOOTH_ADDRESS.matches(address) && name?.isBlank() != true

private fun ExportedSettings.scheduleProblem(): String? {
    val days = schedule.map { DAY_WORDS.constantFor(it.day) }
    val minutes = 0 until MINUTES_PER_DAY
    return when {
        days.size != DayOfWeek.entries.size || days.toSet() != DayOfWeek.entries.toSet() ->
            "the work schedule does not have each of the seven days once"

        schedule.any { it.startMinute !in minutes || it.endMinute !in minutes } ->
            "a day of the work schedule has a time that is no time of day"

        schedule.any { !areValidHours(timeOfMinute(it.startMinute), timeOfMinute(it.endMinute)) } ->
            "a day of the work schedule does not end after it starts"

        else -> null
    }
}

/** A row of "points" that was read, or what is wrong with it. */
internal sealed interface PointRow {
    data class Read(val point: RawPoint) : PointRow

    data class Bad(val why: String) : PointRow
}

/**
 * Reads one row of "points": seven numbers, the last two of which may be null.
 *
 * @param text the row as it stands in the file, such as
 * `[12,1791234567890,5000,53.5,-113.5,4.5,null]`.
 */
internal fun pointOfRow(text: String): PointRow {
    val row =
        try {
            ExportJson.parseToJsonElement(text)
        } catch (unreadable: SerializationException) {
            return PointRow.Bad("it is not JSON (${unreadable.message?.lineSequence()?.first()})")
        }
    if (row !is JsonArray || row.size != POINT_COLUMN_COUNT) {
        return PointRow.Bad("it is not a row of $POINT_COLUMN_COUNT numbers")
    }
    val numbers = row.map { it as? JsonPrimitive }
    if (numbers.any { it == null || it.isString }) {
        return PointRow.Bad("it holds something that is no number")
    }
    val tripId = numbers[0]?.longOrNull
    val wallClockMs = numbers[1]?.longOrNull
    val elapsedMs = numbers[2]?.longOrNull
    val latitude = numbers[3]?.doubleOrNull
    val longitude = numbers[4]?.doubleOrNull
    val accuracy = numbers[5]?.floatOrNull
    val speed = numbers[6]?.floatOrNull
    return when {
        tripId == null || tripId !in 1..MAX_IMPORTED_ID -> PointRow.Bad("its trip is no trip's id")

        wallClockMs == null || wallClockMs < 0 || elapsedMs == null || elapsedMs < 0 ->
            PointRow.Bad("a time of it is not a time")

        latitude == null || longitude == null || !isPosition(latitude, longitude) ->
            PointRow.Bad("its position is not a place on the earth")

        (accuracy == null && numbers[5] != JsonNull) || accuracy?.isFinite() == false ->
            PointRow.Bad("its accuracy is not a number")

        (speed == null && numbers[6] != JsonNull) || speed?.isFinite() == false ->
            PointRow.Bad("its speed is not a number")

        else ->
            PointRow.Read(
                RawPoint(
                    tripId = tripId,
                    wallClockMs = wallClockMs,
                    elapsedRealtimeMs = elapsedMs,
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMetres = accuracy,
                    speedMetresPerSecond = speed,
                ),
            )
    }
}
