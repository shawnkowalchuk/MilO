package com.shawnkowalchuk.milo.feature.eventlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChipChoice
import com.shawnkowalchuk.milo.core.designsystem.component.ChipOption
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.eventlog.EventCategory

// What stands above the lines of the Log screen: the way to share the log, and the filter.

/**
 * The way to send the log to somebody: a text button at the end of its line, like every
 * secondary action, and under it what the file holds, said before it is shared and not after.
 */
@Composable
internal fun ShareLog(share: LogShare, onShare: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onShare, enabled = !share.working) {
                Text(text = stringResource(R.string.log_share))
            }
        }
        Text(
            text =
                stringResource(
                    if (share.working) R.string.log_share_working else R.string.log_share_detail,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        share.problem?.let {
            StatusRow(label = stringResource(it.wordsRes()), status = RowStatus.PROBLEM)
        }
    }
}

/**
 * Narrows the list to one category: "All", then every category by its stored name, the word
 * each line carries.
 */
@Composable
internal fun CategoryFilter(filter: EventCategory?, onFilter: (EventCategory?) -> Unit) {
    val all =
        ChipOption(
            label = stringResource(R.string.log_filter_all),
            selected = filter == null,
            onSelect = { onFilter(null) },
        )
    ChipChoice(
        options =
            listOf(all) +
                EventCategory.entries.map { category ->
                    ChipOption(
                        label = category.name,
                        selected = category == filter,
                        onSelect = { onFilter(category) },
                    )
                },
    )
}

/** The sentence for a press of "Share the log" that did not lead to the share sheet. */
internal fun LogShareProblem.wordsRes(): Int = when (this) {
    LogShareProblem.COULD_NOT_WRITE -> R.string.log_share_failed

    // The Report screen's own words for the same thing.
    LogShareProblem.NO_SHARE_APP -> R.string.report_problem_no_share_app
}
