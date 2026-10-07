package com.shawnkowalchuk.milo.feature.eventlog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChipChoice
import com.shawnkowalchuk.milo.core.designsystem.component.ChipOption
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// What stands above the lines of the Log screen: where a share stands, and the filter; and the
// tile that stands in place of the lines while there are none.

/**
 * Where a press of "Share the whole log" stands, in a tile under the title: that the file is
 * being written, or why the last press did not lead to the share sheet. Shown only while there
 * is one of the two to say.
 */
@Composable
internal fun ShareState(share: LogShare, modifier: Modifier = Modifier) {
    Tile(modifier = modifier.fillMaxWidth()) {
        if (share.working) Note(stringResource(R.string.log_share_working))
        share.problem?.let {
            StatusRow(label = stringResource(it.wordsRes()), status = RowStatus.PROBLEM)
        }
    }
}

/**
 * Narrows the list to one kind of line: "All", then the four chips of the design. Which of the
 * log's categories each chip stands for is `logGroup`.
 */
@Composable
internal fun GroupFilter(
    filter: LogGroup?,
    onFilter: (LogGroup?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all =
        ChipOption(
            label = stringResource(R.string.log_filter_all),
            selected = filter == null,
            onSelect = { onFilter(null) },
        )
    ChipChoice(
        options =
            listOf(all) +
                LogGroup.entries.map { group ->
                    ChipOption(
                        label = stringResource(group.chipRes()),
                        selected = group == filter,
                        onSelect = { onFilter(group) },
                    )
                },
        modifier = modifier,
    )
}

/** One tile with one quiet sentence: the log is being read, or has no line to show. */
@Composable
internal fun NoteTile(text: String) {
    Tile(modifier = Modifier.fillMaxWidth()) { Note(text) }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MiloTheme.textStyles.sentence,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The words on a chip. */
internal fun LogGroup.chipRes(): Int = when (this) {
    LogGroup.TRIPS -> R.string.log_filter_trips
    LogGroup.BLUETOOTH -> R.string.log_filter_bluetooth
    LogGroup.ANDROID_AUTO -> R.string.log_filter_android_auto
    LogGroup.ERRORS -> R.string.log_filter_errors
}

/** The sentence for a list with no line in it: the whole log's, or a chip's. */
internal fun LogGroup?.emptyWordsRes(): Int = when (this) {
    null -> R.string.log_empty
    LogGroup.TRIPS -> R.string.log_empty_trips
    LogGroup.BLUETOOTH -> R.string.log_empty_bluetooth
    LogGroup.ANDROID_AUTO -> R.string.log_empty_android_auto
    LogGroup.ERRORS -> R.string.log_empty_errors
}

/** The sentence for a press of "Share the whole log" that did not lead to the share sheet. */
internal fun LogShareProblem.wordsRes(): Int = when (this) {
    LogShareProblem.COULD_NOT_WRITE -> R.string.log_share_failed

    // The Report screen's own words for the same thing.
    LogShareProblem.NO_SHARE_APP -> R.string.report_problem_no_share_app
}
