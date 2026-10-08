package com.shawnkowalchuk.milo.core.allowance

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// What the business kilometres would be worth at a rate per kilometre, for reference only
// (Shawn's request of 2026-10-07, for the home-screen widget). The report for the accountant
// stays in kilometres: accounts works the money out (the kickoff's "No money on the report"
// stands). Pure functions, tested without a phone.
//
// One rate for every kilometre, set in Settings: Shawn's choice of the day the widget was built
// ("lets just use .70 for a blended rate", then "Settings, starts at 0.70"), in place of the
// CRA's two tiers by year (73¢ for a year's first 5,000 km, 67¢ after, in 2026).

/** The rate out of the box, in cents a kilometre: Shawn's blended 70¢. */
const val DEFAULT_CENTS_PER_KM = 70

/** The lowest rate that can be set, in cents a kilometre. */
const val MIN_CENTS_PER_KM = 1

/** The highest rate that can be set, in cents a kilometre: anything more is taken for a typo. */
const val MAX_CENTS_PER_KM = 500

private const val TENTHS_PER_KM = 10L
private const val HALF_A_CENT_IN_TENTH_CENTS = 5L
private const val CENTS_PER_DOLLAR = 100L
private const val HALF_A_DOLLAR_IN_CENTS = 50L

/** A rate is in whole cents: two digits after the decimal mark. */
private const val CENT_DIGITS = 2

/** Enough digits before the decimal mark for any rate, and far from a number that overflows. */
private const val MAX_WHOLE_DIGITS = 3

/**
 * What [tenths] tenths of a business kilometre are worth at [centsPerKm], in cents, rounded once.
 */
fun allowanceCents(tenths: Long, centsPerKm: Int): Long {
    require(tenths >= 0) { "A distance cannot be negative: $tenths tenths of a km" }
    return (tenths * centsPerKm + HALF_A_CENT_IN_TENTH_CENTS) / TENTHS_PER_KM
}

/**
 * The rate Shawn typed, in dollars a kilometre ("0.70", ".7", "$0.73"), in cents; null if it is
 * not one, or is outside [MIN_CENTS_PER_KM] to [MAX_CENTS_PER_KM]. A comma counts as the decimal
 * mark too: a rate has no thousands to group. A third decimal is refused, not rounded: a rate is
 * in whole cents, and a rounded one would not be the one typed.
 */
fun parseCentsPerKm(typed: String): Int? {
    val bare = typed.trim().removePrefix("$").trim().replace(',', '.')
    val whole = bare.substringBefore('.')
    val fraction = bare.substringAfter('.', missingDelimiterValue = "")
    val wellFormed =
        (whole.isNotEmpty() || fraction.isNotEmpty()) &&
            whole.length <= MAX_WHOLE_DIGITS &&
            fraction.length <= CENT_DIGITS &&
            whole.all { it in '0'..'9' } &&
            fraction.all { it in '0'..'9' }
    if (!wellFormed) return null
    val cents =
        whole.ifEmpty { "0" }.toLong() * CENTS_PER_DOLLAR +
            fraction.padEnd(CENT_DIGITS, '0').toLong()
    return cents.takeIf { it in MIN_CENTS_PER_KM..MAX_CENTS_PER_KM }?.toInt()
}

/**
 * What a rate per kilometre comes to per mile, in whole cents, half a cent upwards: 70¢ a
 * kilometre is 113¢ a mile.
 *
 * **For reading only.** With miles chosen in Settings, the widget's tile says it in a grey line
 * under the rate, so that the rate can be held against a figure in miles. Nothing is ever
 * priced at it: the rate stays a rate per kilometre, as the CRA sets its own, and the dollars
 * are always [allowanceCents] of kilometres, so choosing miles cannot move them by a cent.
 */
fun centsPerMileOf(centsPerKm: Int): Int {
    val kilometresPerMile =
        DistanceUnit.MILES.metresPerUnit.divide(DistanceUnit.KILOMETRES.metresPerUnit)
    return BigDecimal(centsPerKm)
        .multiply(kilometresPerMile)
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact()
}

/**
 * A rate as dollars with two decimals, with the decimal mark of [locale]: "$0.70". The rate per
 * kilometre, and what it comes to per mile ([centsPerMileOf]), are both written with it.
 */
fun formatCentsPerKm(centsPerKm: Int, locale: Locale): String =
    "$" + String.format(locale, "%.2f", BigDecimal.valueOf(centsPerKm.toLong(), CENT_DIGITS))

/** Whole dollars with the thousands grouped as [locale] groups them, rounded: "$1,234". */
fun formatWholeDollars(cents: Long, locale: Locale): String =
    "$" + String.format(locale, "%,d", (cents + HALF_A_DOLLAR_IN_CENTS) / CENTS_PER_DOLLAR)
