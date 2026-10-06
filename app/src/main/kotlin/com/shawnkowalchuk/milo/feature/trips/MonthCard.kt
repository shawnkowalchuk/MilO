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

/** The top of the screen: which month, what it adds up to, and the way to the months beside it. */
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
            Text(
                text =
                    stringResource(
                        R.string.distance_km,
                        formatKilometres(summary.totalMetres, locale),
                    ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.trips_count,
                        summary.tripCount,
                        summary.tripCount,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
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
