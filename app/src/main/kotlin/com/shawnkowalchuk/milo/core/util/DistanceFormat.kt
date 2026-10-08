package com.shawnkowalchuk.milo.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// How a stored distance becomes a figure Shawn or the accountant reads, and how such figures
// are added up. One rule for every screen and for the report, so that no two of them can show
// a different total for the same trips.
//
// Since 2026-10-07 the rule holds in either unit ([DistanceUnit]): a trip is rounded once to a
// tenth of the unit it is shown in, from its stored metres, and a total is the sum of those
// rounded figures. So a month's total in miles is the sum of the miles printed for its trips.
// It is not its kilometre total converted, and it must not be: the column would not add up.

private const val METRES_PER_TENTH_OF_A_KILOMETRE = 100.0

/** Two decimals: what a short distance in miles is written with ([formatShortDistance]). */
private const val HUNDREDTHS = 2

private const val TENTHS_PER_WHOLE = 10L

/**
 * A distance as it is printed in [unit]: whole tenths of it, 12 349 m being 123 tenths of a
 * kilometre and 77 tenths of a mile.
 *
 * Every trip is rounded to a tenth once, here, and nowhere else. Half a tenth goes upwards.
 *
 * **In kilometres the arithmetic is the one this function has always had,** left exactly as
 * it was: a figure in kilometres must not change by a digit because miles exist. In miles the
 * same division is done in decimal arithmetic, because a tenth of a mile is 160.9344 m, which
 * no binary fraction holds: divided as binary fractions, four typed distances in ten that end
 * on half a tenth (0.35 mi, 0.55 mi) would be rounded down.
 *
 * @throws IllegalArgumentException if [metres] is negative, infinite or not a number. A
 * distance like that means the stored trip is corrupt, and a mileage claim should fail loudly
 * rather than print a plausible-looking figure.
 */
fun tenthsOf(metres: Double, unit: DistanceUnit): Long {
    require(metres.isFinite() && metres >= 0.0) {
        "A distance must be a finite, non-negative number of metres, but was $metres"
    }
    return when (unit) {
        DistanceUnit.KILOMETRES -> Math.round(metres / METRES_PER_TENTH_OF_A_KILOMETRE)

        DistanceUnit.MILES ->
            BigDecimal
                .valueOf(metres)
                .divide(unit.metresPerTenth, 0, RoundingMode.HALF_UP)
                .longValueExact()
    }
}

/**
 * What a number of trips add up to, in tenths of [unit]: **the sum of the figures that are
 * printed for them,** each trip rounded to a tenth first.
 *
 * This is the one rule for a total, wherever it is shown: a day's subtotal and the total of the
 * report for the accountant, the month's and each day's figures on the Trips screen, Today on
 * the home screen and on the Android Auto screen. Whoever adds a column of trips up by hand
 * gets the figure printed under it, and a month reads the same on the phone as on the report.
 *
 * The other way round (adding the metres and rounding once) gives a total that the rows above
 * it do not add up to, and that differed from the report's by about a kilometre a month.
 *
 * @param metres each trip's stored distance.
 */
fun sumOfTenths(metres: Iterable<Double>, unit: DistanceUnit): Long =
    metres.sumOf { tenthsOf(it, unit) }

/**
 * Tenths as the figure that is printed, with one decimal: 123 becomes "12.3". Worked out in
 * decimal arithmetic, so the figure is exactly the tenths it is given.
 *
 * Only the number is returned, and it is the same number whichever unit the tenths are of. The
 * unit is user-visible text and lives in strings.xml (`R.string.distance_km`,
 * `R.string.distance_mi`), which also keeps this function free of Android and testable on the
 * plain JVM.
 *
 * @param locale decides the decimal separator. It has no default on purpose: a screen and the
 * PDF pass the phone's locale, while a file meant for a spreadsheet (the CSV) needs
 * [Locale.ROOT] so the separator is always a dot. Each caller has to make that choice.
 */
fun formatTenths(tenths: Long, locale: Locale): String =
    String.format(locale, "%.1f", BigDecimal.valueOf(tenths, 1))

/**
 * The same distance in metres, the unit every stored distance has. Exact: handed back to
 * [tenthsOf] in the same [unit], it gives [tenths] again.
 */
fun metresOfTenths(tenths: Long, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.KILOMETRES -> tenths * METRES_PER_TENTH_OF_A_KILOMETRE
    DistanceUnit.MILES -> BigDecimal.valueOf(tenths).multiply(unit.metresPerTenth).toDouble()
}

/**
 * Turns one distance stored in metres into the figure shown for it in [unit], with one
 * decimal: 23 449 m becomes "23.4" in kilometres and "14.6" in miles.
 *
 * It is [tenthsOf] written out, so the figure a row shows for a trip is the very figure
 * [sumOfTenths] adds up. For one distance only: a total of several trips is never made by
 * adding their metres and handing the sum to this function.
 *
 * @throws IllegalArgumentException for a distance that is negative or not a finite number.
 */
fun formatDistance(metres: Double, unit: DistanceUnit, locale: Locale): String =
    formatTenths(tenthsOf(metres, unit), locale)

/**
 * A short distance that is a setting and not a trip, such as "Shortest trip that counts": in
 * kilometres with one decimal, as every distance is written, and in miles with two, because a
 * tenth of a mile would mislead there. The setting moves in steps of 100 m, and 0.3 km is
 * 0.19 mi: written as 0.2 it would read the same as the step above it.
 *
 * Nothing is added up from this figure, and no trip is judged by it: the setting is stored and
 * compared in metres.
 */
fun formatShortDistance(metres: Double, unit: DistanceUnit, locale: Locale): String = when (unit) {
    DistanceUnit.KILOMETRES -> formatDistance(metres, unit, locale)

    DistanceUnit.MILES -> {
        require(metres.isFinite() && metres >= 0.0) {
            "A distance must be a finite, non-negative number of metres, but was $metres"
        }
        val miles =
            BigDecimal
                .valueOf(metres)
                .divide(unit.metresPerUnit, HUNDREDTHS, RoundingMode.HALF_UP)
        String.format(locale, "%.${HUNDREDTHS}f", miles)
    }
}

/**
 * A distance typed in [unit] as metres, the unit it is stored in: 12.3 km is 12 300 m, and
 * 12.3 mi is 19 794.9312 m. Worked out in decimal arithmetic and turned into a binary fraction
 * once, at the end, so that what was typed is what [tenthsOf] reads back.
 */
fun metresOf(typed: BigDecimal, unit: DistanceUnit): Double =
    typed.multiply(unit.metresPerUnit).toDouble()

/**
 * A whole number of [from] as whole tenths of [to]: an odometer reading typed in one unit, for
 * showing in the other. In the same unit it is the reading itself, times ten, exactly.
 */
fun tenthsOfWhole(whole: Long, from: DistanceUnit, to: DistanceUnit): Long = if (from == to) {
    Math.multiplyExact(whole, TENTHS_PER_WHOLE)
} else {
    tenthsOf(metresOf(BigDecimal.valueOf(whole), from), to)
}

/**
 * A length in metres as whole units of [unit], rounded down: what a limit that is kept in
 * metres is called when a sentence names it in the unit on screen ("more than 1242 mi").
 */
fun wholeUnitsBelow(metres: Double, unit: DistanceUnit): Long =
    BigDecimal.valueOf(metres).divide(unit.metresPerUnit, 0, RoundingMode.DOWN).longValueExact()
