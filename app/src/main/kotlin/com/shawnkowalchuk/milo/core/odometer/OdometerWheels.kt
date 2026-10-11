package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.tenthsOf
import com.shawnkowalchuk.milo.core.util.tenthsOfWhole

// The odometer as Home's row of wheels shows it (Shawn's choice of 2026-10-10: "for the live
// odometer please use build 3", the drawing called "Dashboard wheels"): every digit of the
// whole kilometres on a wheel of its own, one more wheel for the tenth of a kilometre, and that
// last wheel turning as the truck is driven.
//
// **The wheels show the figure Settings shows, written out to the tenth.** Settings' odometer
// tile and the report for the accountant print [OdometerFigure.value]: the reading and the trips
// since, rounded to the nearest whole kilometre or mile. The wheels show the same reading and
// the same trips before that rounding ([inTenths]). So Settings' number is always the wheels'
// number rounded to the nearest whole unit, and whenever the tenth's wheel stands at 5 or more,
// it is one more than the wheels' whole digits: 123,461 in Settings beside 123460 and a 6 here.
// Nothing about [odometerAt] or what the report prints is changed by this file.
//
// **The tenth counts from the reading.** Shawn types the whole kilometres the dashboard shows,
// so MilO does not know where in that kilometre the truck stood. The wheels start at ".0" with
// each reading and count on from there; the truck's own tenth, if it shows one, can differ.

private const val TENTHS_PER_WHOLE = 10L
private const val HALF_IN_TENTHS = 5L

/** The last wheel is never said to be further than this from the tenth the figure shows. */
private const val HALF_A_TENTH = 0.5

/**
 * What the row of wheels shows of one odometer.
 *
 * @param tenths the odometer in tenths of [unit]: the last digit is the tenth's wheel, the
 * digits before it the whole kilometres or miles.
 * @param turn while this odometer counts the trip being recorded: how far the last wheel
 * stands from the tenth [tenths] ends in, in tenths, from -0.5 (half-way up from the digit
 * before) to just under 0.5 (half-way on to the next). Null while it counts no trip: the
 * wheels then stand on their digits.
 */
data class OdometerWheels(val tenths: Long, val unit: DistanceUnit, val turn: Double?)

/**
 * This figure as the wheels show it.
 *
 * @param tripMetres the distance so far of the trip being recorded, if this odometer is
 * counting it (the figure then already holds it, rounded to a tenth), or null.
 */
fun OdometerFigure.wheels(tripMetres: Double? = null): OdometerWheels =
    OdometerWheels(inTenths(), unit, tripMetres?.let { lastWheelTurn(it, unit) })

/**
 * The odometer in tenths of its unit: the reading, as whole tenths, and the trips between the
 * reading and the moment, as [OdometerFigure.drivenTenths] holds them. They are added for a
 * moment after the reading, which "now" always is, and taken away for one before it.
 *
 * **Rounded to the nearest whole unit it is [OdometerFigure.value], always.** That is checked
 * here and not taken on trust: a figure this function cannot account for is shown as its whole
 * number with a tenth of nought, so the wheels can be less exact than Settings' figure but can
 * never say something else.
 */
fun OdometerFigure.inTenths(): Long {
    val readingTenths = tenthsOfWhole(reading.value, from = reading.unit, to = unit)
    val after = readingTenths + drivenTenths
    val before = readingTenths - drivenTenths
    return when {
        wholeOf(after) == value -> after
        before >= 0 && wholeOf(before) == value -> before
        else -> Math.multiplyExact(value, TENTHS_PER_WHOLE)
    }
}

/** Tenths as [odometerAt] rounds them to the whole number it shows: half a unit goes upwards. */
private fun wholeOf(tenths: Long): Long =
    Math.floorDiv(tenths + HALF_IN_TENTHS, TENTHS_PER_WHOLE).coerceAtLeast(0)

/**
 * How far the last wheel stands from the tenth the figure ends in, while a trip of
 * [tripMetres] so far is being counted: the trip's exact distance in tenths of [unit], less
 * the whole tenths it is printed as (`tenthsOf`, the one rounding of a trip).
 *
 * A trip is printed to the nearest tenth, so the wheel stands on a digit when the trip is at
 * exactly that tenth (4,600 m: "4.6"), is half-way up to it 50 m before, and half-way on to the
 * next 50 m after, where the printed figure changes to "4.7". The wheel therefore turns evenly
 * with the distance, and the digit nearest to its window is the one the figure shows.
 */
fun lastWheelTurn(tripMetres: Double, unit: DistanceUnit): Double {
    val exact = tripMetres / unit.metresPerTenth.toDouble()
    return (exact - tenthsOf(tripMetres, unit)).coerceIn(-HALF_A_TENTH, HALF_A_TENTH)
}
