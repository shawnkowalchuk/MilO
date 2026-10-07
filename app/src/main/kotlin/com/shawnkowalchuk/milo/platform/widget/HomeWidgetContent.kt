package com.shawnkowalchuk.milo.platform.widget

import com.shawnkowalchuk.milo.core.allowance.businessAllowance
import com.shawnkowalchuk.milo.core.allowance.formatWholeDollars
import com.shawnkowalchuk.milo.core.util.TimeSpan
import com.shawnkowalchuk.milo.core.util.daysSpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.categoryTotals
import com.shawnkowalchuk.milo.platform.car.CarScreenContent
import com.shawnkowalchuk.milo.platform.car.carScreenContent
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// What the home-screen widget shows (Shawn's request of 2026-10-07), decided from the trip
// controller's state and this year's trips. Plain values and pure functions, tested without a
// phone. The trip's line and the button are the Android Auto screen's own (`carScreenContent`):
// the widget is one more small screen of the same trip, and the two cannot say it differently.

/**
 * The business kilometres of this month and this year, priced at the CRA's per-kilometre rate,
 * as the widget prints them: "Oct $412", "2026 $3,980". For reference only; the report for the
 * accountant stays in kilometres.
 *
 * @param rateYear the year of the rate used. It differs from [yearLabel]'s year when MilO has
 * no rate for this year yet, and the widget then says which year's rate it used.
 */
data class WidgetDollars(
    val monthLabel: String,
    val month: String,
    val yearLabel: String,
    val year: String,
    val rateYear: Int,
    val rateIsTheYears: Boolean,
)

/**
 * Everything the widget shows that can change.
 *
 * @param screen the Android Auto screen's content: the status line, the trip's kilometres and
 * the one button. Today's trips are not shown on the widget.
 * @param tripStartedAtMs when the open trip started, for the running clock; null with no trip.
 * @param dollars null while this year's trips have not been read, or could not be.
 */
data class HomeWidgetContent(
    val screen: CarScreenContent,
    val tripStartedAtMs: Long?,
    val dollars: WidgetDollars?,
)

/**
 * The widget's content for one moment.
 *
 * @param yearTrips the trips that started this year, in any state, or null if not known. Only
 * the counted Business ones are priced, added up as every total in MilO is (`categoryTotals`).
 */
fun homeWidgetContent(
    activity: TripActivity,
    yearTrips: List<Trip>?,
    nowMs: Long,
    zone: ZoneId,
    locale: Locale,
): HomeWidgetContent = HomeWidgetContent(
    screen = carScreenContent(activity, today = null, setupNeedsAttention = false, nowMs, locale),
    tripStartedAtMs = activity.trip?.startedAtMs,
    dollars = yearTrips?.let { widgetDollars(it, nowMs, zone, locale) },
)

/** The span of the calendar year that [nowMs] falls in, in [zone]: the widget's trips. */
fun yearSpanOf(nowMs: Long, zone: ZoneId): TimeSpan {
    val year = localDateOf(nowMs, zone).year
    return daysSpan(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), zone)
}

private fun widgetDollars(
    yearTrips: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
    locale: Locale,
): WidgetDollars {
    val month = YearMonth.from(localDateOf(nowMs, zone))
    val monthStartMs = monthSpan(month, zone).fromMs
    val (before, inMonth) = yearTrips.partition { it.startedAtMs < monthStartMs }
    val priced =
        businessAllowance(
            year = month.year,
            tenthsBeforeMonth = categoryTotals(before).business.tenths,
            monthTenths = categoryTotals(inMonth).business.tenths,
        )
    return WidgetDollars(
        monthLabel = DateTimeFormatter.ofPattern("MMM", locale).format(month),
        month = formatWholeDollars(priced.monthCents, locale),
        yearLabel = month.year.toString(),
        year = formatWholeDollars(priced.yearCents, locale),
        rateYear = priced.rate.year,
        rateIsTheYears = priced.rateIsTheYears,
    )
}
