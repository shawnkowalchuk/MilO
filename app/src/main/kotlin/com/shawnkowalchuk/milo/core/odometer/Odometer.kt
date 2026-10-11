package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.daysSpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.sumOfTenths
import com.shawnkowalchuk.milo.core.util.tenthsOfWhole
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// The truck's odometer as MilO knows it (Shawn's decisions of 2026-10-07): he types the reading
// on the dashboard, and MilO adds the kilometres of every truck trip recorded since. Since
// 2026-10-08 every paired vehicle has an odometer of its own: its readings and its trips. Pure
// functions, so the Settings screen and the report for the accountant work it out alike, and
// the rules are tested without a phone.
//
// Since 2026-10-07 a reading is typed in the unit MilO is set to, and is kept with that unit:
// a dashboard in miles is read in miles, and the figure comes back as it was typed. A reading
// is turned into the other unit only when it is shown in it; nothing stored is converted.

/**
 * The largest reading taken: seven digits, more than any truck's odometer shows, in either
 * unit.
 */
const val MAX_ODOMETER_READING = 9_999_999L

private const val TENTHS_PER_WHOLE = 10L
private const val HALF_IN_TENTHS = 5L
private const val MAX_DIGITS = 7

/**
 * One reading of the odometer that Shawn typed in: the whole number the dashboard shows, the
 * unit it was typed in, and when he typed it. Every reading is kept, corrections too ("each
 * correction is kept with its date").
 *
 * @param value whole kilometres or whole miles, as typed.
 * @param unit which of the two. A reading from before 2026-10-07 is in kilometres.
 * @param vehicle the Bluetooth address of the paired vehicle whose dashboard it was read on
 * (since 2026-10-08, when MilO learned several), or null for a reading typed before: those
 * are the first vehicle's, the truck's ([ofVehicle]).
 * @param duringTrip the trip that was being recorded when it was typed, and how far that trip
 * had gone (since 2026-10-10), or null: no trip was open, or the reading is from before then.
 * The trip is cut there ([cutAtReadings]).
 */
data class OdometerReading(
    val atMs: Long,
    val value: Long,
    val unit: DistanceUnit,
    val vehicle: String? = null,
    val duringTrip: TripAtReading? = null,
)

/**
 * A truck trip as the odometer counts it: when it started, and how far it went. Which trips
 * are truck trips is the data layer's to say (`movesOdometer`): Business, Personal and those
 * not sorted yet, recorded with the truck connected or typed in by hand.
 *
 * @param vehicle the paired vehicle it was in (since 2026-10-08), or null if none was recorded:
 * a trip from before then that has not been given the truck's address yet ([ofVehicle]).
 * @param id the trip's own number (since 2026-10-10), by which a reading typed during it finds
 * it again ([TripAtReading]), or null where nobody needs to.
 */
data class DrivenTrip(
    val startedAtMs: Long,
    val metres: Double,
    val vehicle: String? = null,
    val id: Long? = null,
)

/**
 * The readings of one vehicle's odometer (since 2026-10-08, when MilO learned several): those
 * typed for it, and if it is the first vehicle, the truck, those typed before there were
 * several, which name none.
 */
fun List<OdometerReading>.ofVehicle(address: String, isFirst: Boolean): List<OdometerReading> =
    filter { it.vehicle?.equals(address, ignoreCase = true) ?: isFirst }

/** The trips that moved one vehicle's odometer, on the same terms as its readings. */
fun List<DrivenTrip>.drivenIn(address: String, isFirst: Boolean): List<DrivenTrip> =
    filter { it.vehicle?.equals(address, ignoreCase = true) ?: isFirst }

/**
 * The odometer at one moment.
 *
 * @param value whole kilometres or whole miles: whole units of [unit].
 * @param unit the unit the figure was asked for in. [value] and [drivenTenths] are in it.
 * @param reading the reading it is worked out from, in the unit that one was typed in.
 * @param drivenTenths the truck trips between that reading and the moment, in tenths of
 * [unit], added up as every total in MilO is (`sumOfTenths`). They are added when the moment
 * is after the reading and taken away when it is before.
 * @param estimated false only when the figure is the reading itself: typed on [odometerAt]'s
 * day, with no truck trip between it and the moment. Everything else MilO worked out, and the
 * report marks it "est." so that nobody takes it for a reading off the dashboard (Shawn's
 * choice: "Start and end, marked if estimated"). A reading that is only shown in the other
 * unit is still the reading.
 */
data class OdometerFigure(
    val value: Long,
    val unit: DistanceUnit,
    val reading: OdometerReading,
    val drivenTenths: Long,
    val estimated: Boolean,
)

/**
 * The odometer at [atMs], or null while no reading has been typed in.
 *
 * **Which reading.** Of the readings no later one corrects ([standingReadings]), the one with
 * the fewest truck kilometres between it and [atMs], so a figure is worked out over as little
 * recorded driving as can be; of two as close, the later. That is always measured in
 * kilometres, whatever [unit] is, so the same reading is used in either unit.
 *
 * **How the figure is made.** The reading, in tenths of [unit], and the trips between as they
 * are printed in [unit], each rounded to a tenth; the sum is rounded to a whole unit. A reading
 * typed in [unit] is itself, exactly: with no trip between, the figure is what was typed. One
 * typed in the other unit is turned into [unit] once, from its metres.
 *
 * **Which trips lie between.** A trip counts by when it **started**, as it counts for a day and
 * a month everywhere in MilO: one that started before a reading is in that reading. **The trip
 * that was being recorded when a reading was typed is the exception** (since 2026-10-10): it is
 * cut at that reading ([cutAtReadings]), so what it drove after the reading is added and what
 * it had driven before is not. A reading typed before driving off adds the whole drive; one
 * typed just after parking, with the trip still open, already holds that trip. A reading that
 * does not know its trip's distance (every one typed before 2026-10-10) holds the whole trip,
 * as it always did.
 *
 * @param day the day the figure is for, in [zone]: whether it is the reading as typed depends
 * on it. For the start of a report's period its first day, for the end its last.
 * @param trips truck trips in any order. Those that started outside the span between a
 * reading and [atMs] are ignored, so the caller may pass more.
 * @param unit the unit the figure is shown in.
 */
fun odometerAt(
    atMs: Long,
    day: LocalDate,
    zone: ZoneId,
    readings: List<OdometerReading>,
    trips: List<DrivenTrip>,
    unit: DistanceUnit,
): OdometerFigure? {
    val driven = cutAtReadings(trips, readings)
    val closest =
        standing(readings, driven).minWithOrNull(
            compareBy<OdometerReading> { tenthsBetween(it.atMs, atMs, driven) }
                .thenByDescending { it.atMs },
        ) ?: return null
    val between = tripsBetween(closest.atMs, atMs, driven)
    val tenths = sumOfTenths(between.map { it.metres }, unit)
    val signed = if (atMs >= closest.atMs) tenths else -tenths
    val readingTenths = tenthsOfWhole(closest.value, from = closest.unit, to = unit)
    val whole = Math.floorDiv(readingTenths + signed + HALF_IN_TENTHS, TENTHS_PER_WHOLE)
    return OdometerFigure(
        value = whole.coerceAtLeast(0),
        unit = unit,
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
 *
 * Two readings typed during one trip are held to the same rule (since 2026-10-10): the later
 * corrects the earlier unless the truck drove between the two ([cutAtReadings]).
 */
fun standingReadings(
    readings: List<OdometerReading>,
    trips: List<DrivenTrip>,
): List<OdometerReading> = standing(readings, cutAtReadings(trips, readings))

/** [standingReadings] over trips that are cut at the readings already. */
private fun standing(
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
 * The number on the dashboard in what Shawn typed, or null for anything that is not a reading.
 * Spaces and commas between the digits are allowed ("123,456", "123 456"), and so is a decimal
 * part, which is rounded to the whole kilometre or mile the dashboard shows. Which of the two
 * it is, the caller knows: the unit MilO is set to.
 */
fun parseOdometer(typed: String): Long? {
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
    val reading = whole.toLong() + if (roundsUp) 1 else 0
    return reading.takeIf { it <= MAX_ODOMETER_READING }
}

/** A whole number with the thousands grouped as [locale] groups them: "123,456". */
fun formatOdometer(value: Long, locale: Locale): String = String.format(locale, "%,d", value)

private fun tripsBetween(aMs: Long, bMs: Long, trips: List<DrivenTrip>): List<DrivenTrip> {
    val from = min(aMs, bMs)
    val until = max(aMs, bMs)
    return trips.filter { it.startedAtMs in from until until }
}

/** How much truck driving lies between two moments: the measure a reading is chosen by. */
private fun tenthsBetween(aMs: Long, bMs: Long, trips: List<DrivenTrip>): Long =
    sumOfTenths(tripsBetween(aMs, bMs, trips).map { it.metres }, DistanceUnit.KILOMETRES)

/**
 * The odometer at the start and at the end of a span of days, as the report for the accountant
 * prints it.
 *
 * @param start at the first moment of the first day, the figure for that day.
 * @param end at the last moment of the last day, after every trip that started on it.
 */
data class OdometerSpan(val start: OdometerFigure, val end: OdometerFigure)

/**
 * The odometer over the days [firstDay] to [lastDay] in [zone], in [unit], or null with no
 * reading.
 */
fun odometerOver(
    firstDay: LocalDate,
    lastDay: LocalDate,
    zone: ZoneId,
    readings: List<OdometerReading>,
    trips: List<DrivenTrip>,
    unit: DistanceUnit,
): OdometerSpan? {
    val span = daysSpan(firstDay, lastDay, zone)
    val start = odometerAt(span.fromMs, firstDay, zone, readings, trips, unit) ?: return null
    val end = odometerAt(span.untilMs, lastDay, zone, readings, trips, unit) ?: return null
    return OdometerSpan(start, end)
}
