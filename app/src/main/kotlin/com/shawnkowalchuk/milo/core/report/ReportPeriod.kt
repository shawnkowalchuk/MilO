package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.util.TimeSpan
import com.shawnkowalchuk.milo.core.util.daysSpan
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * What a report covers: a whole calendar month, or a run of days Shawn chose.
 *
 * The two are kept apart even where they cover the same days. Only a report for a whole month
 * marks that month as submitted; a date range from the 1st to the last of a month does not.
 */
sealed interface ReportPeriod {
    /** The first day the report covers. */
    val firstDay: LocalDate

    /** The last day the report covers. It is inside the period. */
    val lastDay: LocalDate

    /** A whole calendar month. */
    data class Month(val month: YearMonth) : ReportPeriod {
        override val firstDay: LocalDate get() = month.atDay(1)
        override val lastDay: LocalDate get() = month.atEndOfMonth()
    }

    /** From [firstDay] to [lastDay], both included. One day is a range too. */
    data class Range(override val firstDay: LocalDate, override val lastDay: LocalDate) :
        ReportPeriod {
        init {
            require(!lastDay.isBefore(firstDay)) {
                "A date range cannot end ($lastDay) before it starts ($firstDay)"
            }
        }
    }
}

/**
 * The span of stored time the period covers in [zone]: from local midnight of its first day to
 * the local midnight that ends its last day. A trip belongs to the period if it **started** in
 * that span, the rule the Trips screen files a trip under its day and month by.
 */
fun ReportPeriod.span(zone: ZoneId): TimeSpan = daysSpan(firstDay, lastDay, zone)

/**
 * The period as it is written on the report, in the email's subject and in the list of sent
 * reports: "October 2026" for a month, "October 5, 2026 to October 18, 2026" for a range, and
 * the one date for a range of a single day.
 *
 * @param rangeWords how two dates are joined, as a format with two places: "%1$s to %2$s". It
 * is user-visible text, so the caller reads it from the string resources.
 */
fun periodInWords(period: ReportPeriod, locale: Locale, rangeWords: String): String =
    when (period) {
        is ReportPeriod.Month -> formatMonthAndYear(period.month, locale)

        is ReportPeriod.Range -> {
            val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
            val first = day.format(period.firstDay)
            if (period.firstDay == period.lastDay) {
                first
            } else {
                String.format(locale, rangeWords, first, day.format(period.lastDay))
            }
        }
    }
