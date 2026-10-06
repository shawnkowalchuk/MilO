package com.shawnkowalchuk.milo.core.util

import java.util.Locale

private const val METRES_PER_KILOMETRE = 1000.0

/**
 * Turns a distance stored in metres into the kilometre figure shown to the user, with one
 * decimal: 23 449 m becomes "23.4".
 *
 * Distances are stored and added up in metres and only converted here, at the moment of display,
 * so rounding never accumulates across the trips of a day or a month.
 *
 * Only the number is returned. The unit is user-visible text and lives in strings.xml
 * (`R.string.distance_km`), which also keeps this function free of Android and testable on the
 * plain JVM.
 *
 * @param locale decides the decimal separator. It has no default on purpose: the screen should
 * pass the phone's locale, while a file meant for a spreadsheet (the CSV export) needs
 * [Locale.ROOT] so the separator is always a dot. Each caller has to make that choice.
 * @throws IllegalArgumentException if [metres] is negative, infinite or not a number. A distance
 * like that means the recorded trip is corrupt, and a mileage claim should fail loudly rather
 * than print a plausible-looking figure.
 */
fun formatKilometres(metres: Double, locale: Locale): String {
    require(metres.isFinite() && metres >= 0.0) {
        "A distance must be a finite, non-negative number of metres, but was $metres"
    }
    return String.format(locale, "%.1f", metres / METRES_PER_KILOMETRE)
}
