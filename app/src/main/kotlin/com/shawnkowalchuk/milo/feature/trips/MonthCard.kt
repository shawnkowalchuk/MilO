package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.text.submissionWords
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.data.report.ChangedSinceSent
import com.shawnkowalchuk.milo.data.report.MonthSubmission
import com.shawnkowalchuk.milo.data.trip.Tally
import java.time.ZoneId
import java.util.Locale

/**
 * The top of the screen: which month, what it adds up to, and the way to the months beside it.
 *
 * Business comes first and in the large figures, because that is what the month's report is
 * made of. Personal follows in a quieter line of its own, so the two can never be read as one
 * total.
 *
 * The figures are added up as the report for the accountant adds up (`sumOfTenths`), so the
 * Business figure here is the total that report prints.
 *
 * Under the figures, whether the month's report has been sent to the accountant, whether the
 * month's Business trips are still what was sent, and the way to the Report screen for this
 * month.
 *
 * @param submission that the month's report was sent, and when; null while it was not.
 * @param changed that the month's Business trips are no longer what its newest report held;
 * null while they are, and for a month that is not submitted.
 * @param onOpenReport opens the Report screen for the month on this card.
 */
@Composable
internal fun MonthCard(
    monthName: String,
    summary: MonthSummary?,
    submission: MonthSubmission?,
    changed: ChangedSinceSent?,
    zone: ZoneId,
    canStepForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = monthName) {
        if (summary != null) {
            val totals = summary.totals
            Text(
                text = stringResource(R.string.trip_business),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = kilometres(totals.business.tenths, locale),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.trips_count,
                        totals.business.count,
                        totals.business.count,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Apart(R.plurals.trips_personal_total, totals.personal, locale)
            // Only while there is such a trip, which in ordinary use is never.
            if (totals.unsorted.count > 0) {
                Apart(R.plurals.trips_unsorted_total, totals.unsorted, locale)
            }
        }
        Text(
            text =
                submissionWords(
                    firstSentAtMs = submission?.first?.sentAtMs,
                    revisions = submission?.revisions ?: 0,
                    sentAgain = submission?.sentAgain ?: false,
                    latestSentAtMs = submission?.latest?.sentAtMs,
                    zone = zone,
                    locale = locale,
                ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (changed != null) ChangedSince(changed, locale)
        // At the end of a line of its own, like every way to another screen.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenReport) {
                Text(text = stringResource(R.string.trips_action_report))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onPreviousMonth) {
                Text(text = stringResource(R.string.trips_previous_month))
            }
            // Greyed out on the current month: a month that has not begun has no trips.
            TextButton(onClick = onNextMonth, enabled = canStepForward) {
                Text(text = stringResource(R.string.trips_next_month))
            }
        }
    }
}

/** A total that is not Business, in a quieter line: "Personal: 22.0 km · 3 trips". */
@Composable
private fun Apart(plural: Int, tally: Tally, locale: Locale) {
    Text(
        text =
            pluralStringResource(
                plural,
                tally.count,
                tally.count,
                kilometres(tally.tenths, locale),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * That the month's Business trips are not what the report that was sent held, with both sets
 * of figures, so that Shawn can tell whether a revision is needed. The way to send one is the
 * button under it.
 */
@Composable
private fun ChangedSince(changed: ChangedSinceSent, locale: Locale) {
    StatusRow(
        label =
            stringResource(
                R.string.trips_changed_since_sent,
                trips(changed.sentTripCount),
                kilometres(changed.sentTenths, locale),
                trips(changed.tripCount),
                kilometres(changed.tenths, locale),
            ),
        status = RowStatus.PROBLEM,
        supportingText = stringResource(R.string.trips_changed_since_sent_detail),
    )
}

@Composable
private fun trips(count: Int): String = pluralStringResource(R.plurals.trips_count, count, count)

/** A total as it is printed, from tenths of a kilometre: "412.3 km". */
@Composable
private fun kilometres(tenths: Long, locale: Locale): String =
    stringResource(R.string.distance_km, formatTenths(tenths, locale))
