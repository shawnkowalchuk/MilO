package com.shawnkowalchuk.milo.feature.eventlog

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.CameToFrontEffect
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.GroupLabel
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitleAction
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import java.time.LocalDate
import java.time.ZoneId

/** What the Log screen can ask for. */
internal class EventLogActions(
    val onShowOlder: () -> Unit,
    val onFilter: (LogGroup?) -> Unit,
    val onShare: () -> Unit,
)

/**
 * The stored event log, newest first: Shawn's only window into why a trip did or did not start.
 * It is laid out as the owner's design draws it. Each day's lines are one tile; a line shows
 * its time to the second, in the phone's own time zone, a small coloured tag for its kind, and
 * its message. A line that has more to say (the state before and after a trigger, a stack
 * trace) opens when it is pressed.
 *
 * The chips narrow the list to one kind of line. And the whole log can be shared as a text
 * file, with the square button beside the title, through Android's share sheet, so that it can
 * be sent to whoever is helping without plugging the phone into a computer.
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
    // Which entry is open, and whether the question before a share is showing. Both belong to
    // the screen, not the log, so they are kept here; saved, so a rotation does not close the
    // stack trace being read.
    var openEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var askingToShare by rememberSaveable { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    // Which day is "today" is looked up again whenever the list changes and whenever the
    // screen is looked at again: MilO can stay open past midnight.
    var looks by remember { mutableIntStateOf(0) }
    CameToFrontEffect { looks++ }
    val entries = state?.entries.orEmpty()
    val days = remember(entries, zone, looks) { logDays(entries, zone, LocalDate.now(zone)) }
    val timeWidth = rememberLogTimeWidth()
    val spacing = MiloTheme.spacing

    // A lazy list: only the rows on screen are laid out, however many have been read.
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.small),
    ) {
        item {
            ScreenTitle(
                text = stringResource(R.string.log_title),
                action =
                    ScreenTitleAction(
                        icon = MiloIcons.Share,
                        description = stringResource(R.string.log_share),
                        onClick = { askingToShare = true },
                        // The button waits while the file is being written.
                        enabled = !share.working,
                    ),
            )
        }
        if (share.working || share.problem != null) {
            item { ShareState(share, Modifier.padding(top = spacing.rowGap)) }
        }
        item {
            GroupFilter(
                filter = state?.filter,
                onFilter = actions.onFilter,
                modifier = Modifier.padding(vertical = spacing.rowGap),
            )
        }
        when {
            state == null -> item { NoteTile(stringResource(R.string.log_reading)) }
            entries.isEmpty() -> item { NoteTile(stringResource(state.filter.emptyWordsRes())) }
        }
        logDays(
            days = days,
            zone = zone,
            timeWidth = timeWidth,
            openEntryId = openEntryId,
            onToggle = { id -> openEntryId = if (openEntryId == id) null else id },
        )
        if (state?.hasOlder == true) {
            item {
                TextButton(onClick = actions.onShowOlder, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.log_show_older))
                }
            }
        }
    }

    if (askingToShare) {
        // What the file holds is said before it is made, and not after.
        ConfirmDialog(
            title = stringResource(R.string.log_share_question),
            text = stringResource(R.string.log_share_detail),
            confirmLabel = stringResource(R.string.log_share_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                askingToShare = false
                actions.onShare()
            },
            onDismiss = { askingToShare = false },
        )
    }
}

/**
 * The days and their lines. Each day but today is named above its tile, and the name stays at
 * the top of the list while that day's lines are scrolled through, so that a time is never
 * read without its date.
 */
private fun LazyListScope.logDays(
    days: List<LogDay>,
    zone: ZoneId,
    timeWidth: LogTimeWidth,
    openEntryId: Long?,
    onToggle: (Long) -> Unit,
) {
    days.forEachIndexed { index, day ->
        if (!day.isToday) {
            stickyHeader(key = day.date.toString(), contentType = LogDay::class) {
                DayLabel(day.date, isFirst = index == 0)
            }
        }
        items(items = day.lines, key = { it.entry.id }, contentType = { LogDayLine::class }) {
            LogLine(
                line = it,
                zone = zone,
                timeWidth = timeWidth,
                isOpen = it.entry.id == openEntryId,
                onToggle = { onToggle(it.entry.id) },
            )
        }
    }
}

/**
 * A day's name above its tile, on a strip of the page's colour as wide as the tiles: while it
 * stays at the top of the list, the lines pass under it.
 *
 * @param isFirst the newest line is not from today, so this label follows the chips, which
 * already keep their distance.
 */
@Composable
private fun DayLabel(date: LocalDate, isFirst: Boolean) {
    val spacing = MiloTheme.spacing
    Box(
        modifier =
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(
                top = if (isFirst) spacing.textGap else spacing.tileGap,
                bottom = spacing.tileGap,
            ),
    ) {
        GroupLabel(text = formatLogDay(date))
    }
}

// Sample values are written inline because a preview is never shown to a user or shipped.
@Preview
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
