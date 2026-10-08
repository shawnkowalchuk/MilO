package com.shawnkowalchuk.milo.platform.widget

import com.shawnkowalchuk.milo.core.allowance.allowanceCents
import com.shawnkowalchuk.milo.core.allowance.formatCentsPerKm
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
 * The business kilometres of this month and this year, priced at the rate set in Settings, as
 * the widget prints them: "Oct $412", "2026 $3,980", "$0.70". For reference only; the report
 * for the accountant stays in kilometres.
 *
 * @param rate the rate they are priced at, in dollars a kilometre, for the widget to name.
 */
data class WidgetDollars(
    val monthLabel: String,
    val month: String,
    val yearLabel: String,
    val year: String,
    val rate: String,
)

/**
 * Everything the widget shows that can change.
 *
 * @param screen the Android Auto screen's content: the status line, the trip's kilometres and
 * the one button. Today's trips are not shown on the widget.
 * @param tripStartedAtMs when the open trip started, for the running clock; null with no trip.
 * @param dollars null while this year's trips or the rate have not been read, or could not be.
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
 * @param centsPerKm the rate set in Settings, or null if the settings could not be read.
 */
fun homeWidgetContent(
    activity: TripActivity,
    yearTrips: List<Trip>?,
    centsPerKm: Int?,
    nowMs: Long,
    zone: ZoneId,
    locale: Locale,
): HomeWidgetContent = HomeWidgetContent(
    screen = carScreenContent(activity, today = null, setupNeedsAttention = false, nowMs, locale),
    tripStartedAtMs = activity.trip?.startedAtMs,
    dollars =
        if (yearTrips != null && centsPerKm != null) {
            widgetDollars(yearTrips, centsPerKm, nowMs, zone, locale)
        } else {
            null
        },
)

/** The span of the calendar year that [nowMs] falls in, in [zone]: the widget's trips. */
fun yearSpanOf(nowMs: Long, zone: ZoneId): TimeSpan {
    val year = localDateOf(nowMs, zone).year
    return daysSpan(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), zone)
}

private fun widgetDollars(
    yearTrips: List<Trip>,
    centsPerKm: Int,
    nowMs: Long,
    zone: ZoneId,
    locale: Locale,
): WidgetDollars {
    val month = YearMonth.from(localDateOf(nowMs, zone))
    val monthStartMs = monthSpan(month, zone).fromMs
    val inMonth = yearTrips.filter { it.startedAtMs >= monthStartMs }
    fun priced(trips: List<Trip>): String = formatWholeDollars(
        allowanceCents(categoryTotals(trips).business.tenths, centsPerKm),
        locale,
    )
    return WidgetDollars(
        monthLabel = DateTimeFormatter.ofPattern("MMM", locale).format(month),
        month = priced(inMonth),
        yearLabel = month.year.toString(),
        year = priced(yearTrips),
        rate = formatCentsPerKm(centsPerKm, locale),
    )
}
