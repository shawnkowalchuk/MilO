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
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.data.trip.Tally
import java.util.Locale

/**
 * The top of the screen: which month, what it adds up to, and the way to the months beside it.
 *
 * Business comes first and in the large figures, because that is what the month's report will
 * be made of. Personal follows in a quieter line of its own, so the two can never be read as
 * one total.
 */
@Composable
internal fun MonthCard(
    monthName: String,
    summary: MonthSummary?,
    canStepForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
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
                text = kilometres(totals.business.metres, locale),
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
                kilometres(tally.metres, locale),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun kilometres(metres: Double, locale: Locale): String =
    stringResource(R.string.distance_km, formatKilometres(metres, locale))
