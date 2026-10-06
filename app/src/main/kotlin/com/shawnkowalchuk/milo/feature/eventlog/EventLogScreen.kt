package com.shawnkowalchuk.milo.feature.eventlog

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatLogTime
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import java.time.ZoneId

/** What the Log screen can ask for. */
internal class EventLogActions(
    val onShowOlder: () -> Unit,
    val onFilter: (EventCategory?) -> Unit,
    val onShare: () -> Unit,
)

/**
 * The stored event log, newest first: Shawn's only window into why a trip did or did not start.
 * Every line shows its time to the second, in the phone's own time zone, its category and its
 * message. A line that has more to say (the state before and after a trigger, a stack trace)
 * opens when it is pressed.
 *
 * The list can be narrowed to the lines of one category. And the whole log can be shared as a
 * text file, through Android's share sheet, so that it can be sent to whoever is helping
 * without plugging the phone into a computer.
 */
@Composable
fun EventLogScreen(viewModel: EventLogViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val share by viewModel.share.collectAsState()
    val shareTitle = stringResource(R.string.log_share_title)

    // The share sheet is opened on MilO's own activity, so that Back from it leads here. What
    // comes back says nothing: sharing has no result.
    val shareSheet =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val launch = share.launch
    LaunchedEffect(launch?.id) {
        if (launch != null) {
            val opened =
                try {
                    shareSheet.launch(launch.intent)
                    true
                } catch (noSuchApp: ActivityNotFoundException) {
                    // A phone with nothing to share a file with. The screen says so.
                    false
                }
            viewModel.onShareLaunched(launch, opened)
        }
    }

    EventLogContent(
        state = state,
        share = share,
        actions =
            EventLogActions(
                onShowOlder = viewModel::onShowOlder,
                onFilter = viewModel::onFilter,
                onShare = { viewModel.onShare(shareTitle) },
            ),
        modifier = modifier,
    )
}

@Composable
private fun EventLogContent(
    state: EventLogUiState?,
    share: LogShare,
    actions: EventLogActions,
    modifier: Modifier = Modifier,
) {
    // Which entry is open. It belongs to the screen, not the log, so it is kept here; saved, so
    // a rotation does not close the stack trace being read.
    var openEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val zone = ZoneId.systemDefault()
    val filter = state?.filter

    // A lazy list: only the rows on screen are laid out, however many have been read.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
    ) {
        item { ScreenTitle(text = stringResource(R.string.log_title)) }
        item { ShareLog(share, actions.onShare) }
        item { CategoryFilter(filter, actions.onFilter) }
        when {
            state == null -> item { Note(stringResource(R.string.log_reading)) }

            state.entries.isNotEmpty() -> Unit

            // The category's own name, as the lines carry it.
            filter != null -> item {
                Note(stringResource(R.string.log_empty_filtered, filter.name))
            }

            else -> item { Note(stringResource(R.string.log_empty)) }
        }
        items(items = state?.entries.orEmpty(), key = { it.id }) { entry ->
            LogEntryRow(
                entry = entry,
                zone = zone,
                isOpen = entry.id == openEntryId,
                onToggle = { openEntryId = if (openEntryId == entry.id) null else entry.id },
            )
            HorizontalDivider()
        }
        if (state?.hasOlder == true) {
            item {
                TextButton(onClick = actions.onShowOlder, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.log_show_older))
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun LogEntryRow(
    entry: EventLogEntry,
    zone: ZoneId,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    val detail = entry.detail
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // Only a line with something more to show reacts to a press.
                .clickable(enabled = detail != null, onClick = onToggle),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small)) {
            Text(
                text = formatLogTime(entry.atMs, zone),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                // The category's own name, as it is stored: the log is evidence, in English.
                text = entry.category.name,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (entry.category.isFailure()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
        Text(text = entry.message, style = MaterialTheme.typography.bodyMedium)
        if (detail != null) {
            Text(
                text = if (isOpen) detail else stringResource(R.string.log_has_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun EventCategory.isFailure(): Boolean =
    this == EventCategory.CRASH || this == EventCategory.ERROR

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun EventLogPreview() {
    val entries =
        listOf(
            EventLogEntry(
                id = 3,
                atMs = 1_791_028_803_000,
                category = EventCategory.TRIP,
                message = "Trip 12 started by TRUCK",
            ),
            EventLogEntry(
                id = 2,
                atMs = 1_791_028_801_000,
                category = EventCategory.TRIGGER,
                message = "Bluetooth receiver: ACL connected for the truck",
                detail = "before: idle\nafter: recording",
            ),
            EventLogEntry(
                id = 1,
                atMs = 1_791_028_800_000,
                category = EventCategory.ERROR,
                message = "The settings could not be read",
                detail = "java.io.IOException",
            ),
        )
    MiloTheme {
        Surface {
            EventLogContent(
                state = EventLogUiState(entries, hasOlder = true),
                share = LogShare(problem = LogShareProblem.COULD_NOT_WRITE),
                actions = EventLogActions({}, {}, {}),
            )
        }
    }
}
