package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.daysSpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.sumOfTenths
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// The truck's odometer as MilO knows it (Shawn's decisions of 2026-10-07): he types the reading
// on the dashboard, and MilO adds the kilometres of every truck trip recorded since. Pure
// functions, so the Settings screen and the report for the accountant work it out alike, and
// the rules are tested without a phone.

/** The largest reading taken: seven digits, more than any truck's odometer shows. */
const val MAX_ODOMETER_KM = 9_999_999L

private const val TENTHS_PER_KM = 10L
private const val HALF_A_KM_IN_TENTHS = 5L
private const val MAX_DIGITS = 7

/**
 * One reading of the odometer that Shawn typed in: whole kilometres, as the dashboard shows
 * them, and when he typed it. Every reading is kept, corrections too ("each correction is kept
 * with its date").
 */
data class OdometerReading(val atMs: Long, val km: Long)

/**
 * A truck trip as the odometer counts it: when it started, and how far it went. Which trips
 * are truck trips is the data layer's to say (`movesOdometer`): Business, Personal and those
 * not sorted yet, recorded with the truck connected or typed in by hand.
 */
data class DrivenTrip(val startedAtMs: Long, val metres: Double)

/**
 * The odometer at one moment.
 *
 * @param km whole kilometres.
 * @param reading the reading it is worked out from.
 * @param drivenTenths the truck trips between that reading and the moment, in tenths of a
 * kilometre, added up as every total in MilO is (`sumOfTenths`). They are added when the
 * moment is after the reading and taken away when it is before.
 * @param estimated false only when the figure is the reading itself: typed on [odometerAt]'s
 * day, with no truck trip between it and the moment. Everything else MilO worked out, and the
 * report marks it "est." so that nobody takes it for a reading off the dashboard (Shawn's
 * choice: "Start and end, marked if estimated").
 */
data class OdometerFigure(
    val km: Long,
    val reading: OdometerReading,
    val drivenTenths: Long,
    val estimated: Boolean,
)

/**
 * The odometer at [atMs], or null while no reading has been typed in.
 *
 * **Which reading.** Of the readings no later one corrects ([standingReadings]), the one with
 * the fewest truck kilometres between it and [atMs], so a figure is worked out over as little
 * recorded driving as can be; of two as close, the later.
 *
 * **Which trips lie between.** A trip counts by when it **started**, as it counts for a day and
 * a month everywhere in MilO: one that started before a reading is in that reading. So a
 * reading typed just after parking, with the trip still open, already holds that trip.
 *
 * @param day the day the figure is for, in [zone]: whether it is the reading as typed depends
 * on it. For the start of a report's period its first day, for the end its last.
 * @param trips truck trips in any order. Those that started outside the span between a
 * reading and [atMs] are ignored, so the caller may pass more.
 */
fun odometerAt(
    atMs: Long,
    day: LocalDate,
    zone: ZoneId,
    readings: List<OdometerReading>,
    trips: List<DrivenTrip>,
): OdometerFigure? {
    val closest =
        standingReadings(readings, trips).minWithOrNull(
            compareBy<OdometerReading> { tenthsBetween(it.atMs, atMs, trips) }
                .thenByDescending { it.atMs },
        ) ?: return null
    val between = tripsBetween(closest.atMs, atMs, trips)
    val tenths = sumOfTenths(between.map { it.metres })
    val signed = if (atMs >= closest.atMs) tenths else -tenths
    val km = Math.floorDiv(closest.km * TENTHS_PER_KM + signed + HALF_A_KM_IN_TENTHS, TENTHS_PER_KM)
    return OdometerFigure(
        km = km.coerceAtLeast(0),
        reading = closest,
        drivenTenths = tenths,
        estimated = between.isNotEmpty() || localDateOf(closest.atMs, zone) != day,
    )
}

/**
 * The readings that still stand, oldest first: each one, unless a later reading follows it with
 * no truck trip between the two. That later one is a correction of it (a figure typed wrong,
 * typed again a minute later) and takes its place; without this, the wrong figure would still
 * be the closest reading to every moment before the correction.
 */
fun standingReadings(
    readings: List<OdometerReading>,
    trips: List<DrivenTrip>,
): List<OdometerReading> {
    val oldestFirst = readings.sortedBy { it.atMs }
    return oldestFirst.filterIndexed { index, reading ->
        val next = oldestFirst.getOrNull(index + 1)
        next == null || tripsBetween(reading.atMs, next.atMs, trips).isNotEmpty()
    }
}

/**
 * The kilometres on the dashboard in what Shawn typed, or null for anything that is not a
 * reading. Spaces and commas between the digits are allowed ("123,456", "123 456"), and so is a
 * decimal part, which is rounded to the whole kilometre the dashboard shows.
 */
fun parseOdometerKm(typed: String): Long? {
    val bare = typed.trim().filterNot { it == ' ' || it == ',' || it == ' ' || it == ' ' }
    val whole = bare.substringBefore('.')
    val fraction = bare.substringAfter('.', missingDelimiterValue = "")
    val wellFormed =
        whole.length in 1..MAX_DIGITS &&
            whole.all { it.isDigit() } &&
            fraction.all { it.isDigit() } &&
            !(bare.endsWith('.'))
    if (!wellFormed) return null
    val roundsUp = fraction.firstOrNull()?.let { it >= '5' } ?: false
    val km = whole.toLong() + if (roundsUp) 1 else 0
    return km.takeIf { it <= MAX_ODOMETER_KM }
}

/** Whole kilometres with the thousands grouped as [locale] groups them: "123,456". */
fun formatOdometerKm(km: Long, locale: Locale): String = String.format(locale, "%,d", km)

private fun tripsBetween(aMs: Long, bMs: Long, trips: List<DrivenTrip>): List<DrivenTrip> {
    val from = min(aMs, bMs)
    val until = max(aMs, bMs)
    return trips.filter { it.startedAtMs in from until until }
}

private fun tenthsBetween(aMs: Long, bMs: Long, trips: List<DrivenTrip>): Long =
    sumOfTenths(tripsBetween(aMs, bMs, trips).map { it.metres })

/**
 * The odometer at the start and at the end of a span of days, as the report for the accountant
 * prints it.
 *
 * @param start at the first moment of the first day, the figure for that day.
 * @param end at the last moment of the last day, after every trip that started on it.
 */
data class OdometerSpan(val start: OdometerFigure, val end: OdometerFigure)

/** The odometer over the days [firstDay] to [lastDay] in [zone], or null with no reading. */
fun odometerOver(
    firstDay: LocalDate,
    lastDay: LocalDate,
    zone: ZoneId,
    readings: List<OdometerReading>,
    trips: List<DrivenTrip>,
): OdometerSpan? {
    val span = daysSpan(firstDay, lastDay, zone)
    val start = odometerAt(span.fromMs, firstDay, zone, readings, trips) ?: return null
    val end = odometerAt(span.untilMs, lastDay, zone, readings, trips) ?: return null
    return OdometerSpan(start, end)
}
