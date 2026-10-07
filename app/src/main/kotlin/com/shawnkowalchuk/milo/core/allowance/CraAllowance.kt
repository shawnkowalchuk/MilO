package com.shawnkowalchuk.milo.core.allowance

import java.util.Locale

// What the business kilometres would be worth at the CRA's per-kilometre rate, for reference
// only (Shawn's request of 2026-10-07, for the home-screen widget). The report for the
// accountant stays in kilometres: accounts works the money out (the kickoff's "No money on the
// report" stands). Pure functions, tested without a phone.

/**
 * The tax-exempt per-kilometre allowance for one calendar year in the provinces, as the
 * Department of Finance announces it each year and the CRA applies it: one rate for the first
 * [CRA_FIRST_TIER_KM] business kilometres of the year, another for every kilometre after.
 */
data class CraRate(val year: Int, val firstTierCents: Int, val afterCents: Int)

/** The business kilometres of a year that are paid at [CraRate.firstTierCents]. */
const val CRA_FIRST_TIER_KM = 5_000L

/**
 * The rates MilO knows, oldest first. A new year's rates come with an update of MilO (Shawn's
 * choice of 2026-10-07: "Built in, updated each year"); until then [craRateFor] uses the newest.
 *
 * 2026: announced by the Department of Finance on 2026-01-14, 73¢ and 67¢. 2025: 72¢ and 66¢.
 * 2024: 70¢ and 64¢. The territories' rates (4¢ more) do not apply to a truck in Alberta.
 */
val CRA_RATES: List<CraRate> =
    listOf(
        CraRate(year = 2024, firstTierCents = 70, afterCents = 64),
        CraRate(year = 2025, firstTierCents = 72, afterCents = 66),
        CraRate(year = 2026, firstTierCents = 73, afterCents = 67),
    )

/**
 * The rate for [year]: its own, or the newest MilO knows if it has none (a year whose rates
 * came after this build), or the oldest for a year before every known one.
 */
fun craRateFor(year: Int, rates: List<CraRate> = CRA_RATES): CraRate =
    rates.lastOrNull { it.year <= year } ?: rates.first()

private const val TENTHS_PER_KM = 10L
private const val HALF_A_CENT_IN_TENTH_CENTS = 5L

/**
 * What [tenths] tenths of a business kilometre are worth at [rate], in cents, when
 * [tenthsBefore] tenths had already been driven on business in the same year: the part below
 * the year's first [CRA_FIRST_TIER_KM] km at the first rate, the rest at the second. Rounded to
 * the cent once, at the end.
 */
fun allowanceCents(tenthsBefore: Long, tenths: Long, rate: CraRate): Long {
    require(tenthsBefore >= 0 && tenths >= 0) { "Distances cannot be negative" }
    val tierEnd = CRA_FIRST_TIER_KM * TENTHS_PER_KM
    val inFirstTier = (tierEnd - tenthsBefore).coerceIn(0, tenths)
    val after = tenths - inFirstTier
    val tenthCents = inFirstTier * rate.firstTierCents + after * rate.afterCents
    return (tenthCents + HALF_A_CENT_IN_TENTH_CENTS) / TENTHS_PER_KM
}

/**
 * The business kilometres of this month and of this year so far, priced.
 *
 * @param monthCents what this month's business kilometres are worth, priced where they fall in
 * the year: after the kilometres of the months before.
 * @param yearCents what every business kilometre since January 1 is worth.
 * @param rate the rate used, the year's own or the newest known ([rateIsTheYears]).
 */
data class BusinessAllowance(
    val monthCents: Long,
    val yearCents: Long,
    val rate: CraRate,
    val rateIsTheYears: Boolean,
)

/**
 * Prices the business kilometres of [year]: [tenthsBeforeMonth] driven in its months before
 * this one, [monthTenths] in this one. Both are added up as every total in MilO is
 * (`sumOfTenths`), so the kilometres are the ones the Trips screen shows.
 */
fun businessAllowance(
    year: Int,
    tenthsBeforeMonth: Long,
    monthTenths: Long,
    rates: List<CraRate> = CRA_RATES,
): BusinessAllowance {
    val rate = craRateFor(year, rates)
    return BusinessAllowance(
        monthCents = allowanceCents(tenthsBeforeMonth, monthTenths, rate),
        yearCents = allowanceCents(0, tenthsBeforeMonth + monthTenths, rate),
        rate = rate,
        rateIsTheYears = rate.year == year,
    )
}

private const val CENTS_PER_DOLLAR = 100L
private const val HALF_A_DOLLAR_IN_CENTS = 50L

/** Whole dollars with the thousands grouped as [locale] groups them, rounded: "$1,234". */
fun formatWholeDollars(cents: Long, locale: Locale): String =
    "$" + String.format(locale, "%,d", (cents + HALF_A_DOLLAR_IN_CENTS) / CENTS_PER_DOLLAR)
