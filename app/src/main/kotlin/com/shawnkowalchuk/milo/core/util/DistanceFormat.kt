package com.shawnkowalchuk.milo.core.util

import java.math.BigDecimal
import java.util.Locale

// How a stored distance becomes a figure Shawn or the accountant reads, and how such figures
// are added up. One rule for every screen and for the report, so that no two of them can show
// a different total for the same trips.

private const val METRES_PER_TENTH_OF_A_KILOMETRE = 100.0

/**
 * A distance as it is printed: whole tenths of a kilometre, 12 349 m being 123.
 *
 * Every trip is rounded to a tenth of a kilometre once, here, and nowhere else.
 *
 * @throws IllegalArgumentException if [metres] is negative, infinite or not a number. A
 * distance like that means the stored trip is corrupt, and a mileage claim should fail loudly
 * rather than print a plausible-looking figure.
 */
fun tenthsOfAKilometre(metres: Double): Long {
    require(metres.isFinite() && metres >= 0.0) {
        "A distance must be a finite, non-negative number of metres, but was $metres"
    }
    return Math.round(metres / METRES_PER_TENTH_OF_A_KILOMETRE)
}

/**
 * What a number of trips add up to, in tenths of a kilometre: **the sum of the figures that
 * are printed for them,** each trip rounded to a tenth first.
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
fun sumOfTenths(metres: Iterable<Double>): Long = metres.sumOf { tenthsOfAKilometre(it) }

/**
 * Tenths of a kilometre as the kilometre figure that is printed, with one decimal: 123 becomes
 * "12.3". Worked out in decimal arithmetic, so the figure is exactly the tenths it is given.
 *
 * Only the number is returned. The unit is user-visible text and lives in strings.xml
 * (`R.string.distance_km`), which also keeps this function free of Android and testable on the
 * plain JVM.
 *
 * @param locale decides the decimal separator. It has no default on purpose: a screen and the
 * PDF pass the phone's locale, while a file meant for a spreadsheet (the CSV) needs
 * [Locale.ROOT] so the separator is always a dot. Each caller has to make that choice.
 */
fun formatTenths(tenths: Long, locale: Locale): String =
    String.format(locale, "%.1f", BigDecimal.valueOf(tenths, 1))

/** The same distance in metres, the unit every stored distance has. */
fun metresOfTenths(tenths: Long): Double = tenths * METRES_PER_TENTH_OF_A_KILOMETRE

/**
 * Turns one distance stored in metres into the kilometre figure shown for it, with one
 * decimal: 23 449 m becomes "23.4".
 *
 * It is [tenthsOfAKilometre] written out, so the figure a row shows for a trip is the very
 * figure [sumOfTenths] adds up. For one distance only: a total of several trips is never made
 * by adding their metres and handing the sum to this function.
 *
 * @throws IllegalArgumentException for a distance that is negative or not a finite number.
 */
fun formatKilometres(metres: Double, locale: Locale): String =
    formatTenths(tenthsOfAKilometre(metres), locale)
