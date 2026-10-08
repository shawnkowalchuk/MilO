package com.shawnkowalchuk.milo.feature.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.GroupLabel
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tag
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TileRow
import com.shawnkowalchuk.milo.core.designsystem.component.tileRowPlace
import com.shawnkowalchuk.milo.core.designsystem.theme.FillAndText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.data.changelog.Change
import com.shawnkowalchuk.milo.data.changelog.ChangeKind
import java.time.LocalDate

/**
 * Every version of MilO and what changed in it, newest first, as GopherForms' and SeaWingman's
 * "What's new" lists them (Shawn's request of 2026-10-08). Opened from the Version tile on
 * Settings, and once by itself after an update.
 *
 * Each version is a grey label on the page, its number and the day it was released, over a tile
 * with a row for each change: a tag for its kind (New, Improved, Fixed, Security), its title and
 * a sentence or two. Made of the design's parts: the event log's tag, the checklist's rows.
 *
 * @param onBack the arrow at the top. Android's Back does the same.
 */
@Composable
fun WhatsNewScreen(
    viewModel: WhatsNewViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    WhatsNewContent(state = state, onBack = onBack, modifier = modifier)
}

@Composable
private fun WhatsNewContent(state: WhatsNewUiState, onBack: () -> Unit, modifier: Modifier) {
    val spacing = MiloTheme.spacing
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // The list grows with every version: it scrolls.
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.small),
    ) {
        AppHeader(
            title = stringResource(R.string.whats_new_title),
            onBack = onBack,
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        when (state) {
            WhatsNewUiState.Reading ->
                NoteTile {
                    Text(
                        text = stringResource(R.string.whats_new_reading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            WhatsNewUiState.Unreadable ->
                NoteTile {
                    StatusRow(
                        label = stringResource(R.string.whats_new_unreadable),
                        status = RowStatus.PROBLEM,
                    )
                }

            is WhatsNewUiState.Ready -> for (release in state.releases) ReleaseGroup(release)
        }
    }
}

/** One version: its label on the page, then its changes as the rows of one tile. */
@Composable
private fun ReleaseGroup(release: ReleaseShown) {
    GroupLabel(releaseHeading(release))
    Column {
        release.changes.forEachIndexed { index, change ->
            TileRow(place = tileRowPlace(index, release.changes.size)) { ChangeLine(change) }
        }
    }
}

/** "0.1.0 · 9 Oct 2026", or "0.1.0 · not released yet", and "· on this phone" after the one. */
@Composable
private fun releaseHeading(release: ReleaseShown): String {
    val locale = LocalConfiguration.current.locales[0]
    val day = release.releasedOn
    val heading =
        stringResource(
            R.string.whats_new_release,
            release.version,
            if (day == null) {
                stringResource(R.string.whats_new_not_released)
            } else {
                formatMediumDay(day, locale)
            },
        )
    return if (release.onThisPhone) {
        stringResource(R.string.whats_new_this_phone, heading)
    } else {
        heading
    }
}

/** A change: its tag, its title, and what it says under it. Read as one by a screen reader. */
@Composable
private fun ChangeLine(change: Change) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
    ) {
        Tag(text = change.kind.tagWord(), colors = change.kind.tagColors())
        Text(text = change.title, style = MaterialTheme.typography.titleSmall)
        val body = change.body
        if (body != null) {
            Text(
                text = body,
                style = MiloTheme.textStyles.sentence,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChangeKind.tagWord(): String = stringResource(
    when (this) {
        ChangeKind.NEW -> R.string.whats_new_tag_new
        ChangeKind.IMPROVED -> R.string.whats_new_tag_improved
        ChangeKind.FIXED -> R.string.whats_new_tag_fixed
        ChangeKind.SECURITY -> R.string.whats_new_tag_security
    },
)

@Composable
private fun ChangeKind.tagColors(): FillAndText {
    val tags = MiloTheme.colors.changeTags
    return when (this) {
        ChangeKind.NEW -> tags.new
        ChangeKind.IMPROVED -> tags.improved
        ChangeKind.FIXED -> tags.fixed
        ChangeKind.SECURITY -> tags.security
    }
}

/** A tile for the one thing the screen has to say in place of the list. */
@Composable
private fun NoteTile(content: @Composable () -> Unit) {
    Tile(modifier = Modifier.fillMaxWidth(), padding = TilePadding.EVEN) { content() }
}

@Preview
@Composable
private fun WhatsNewPreview() {
    val changes =
        listOf(
            Change(ChangeKind.NEW, "Several vehicles", "Pair each of your work trucks."),
            Change(ChangeKind.IMPROVED, "Faster start", null),
            Change(ChangeKind.FIXED, "The odometer after an import", "It counts again."),
            Change(ChangeKind.SECURITY, "Newer libraries", null),
        )
    val state =
        WhatsNewUiState.Ready(
            listOf(
                ReleaseShown("0.2.0", null, onThisPhone = true, changes = changes),
                ReleaseShown("0.1.0", LocalDate.of(2026, 10, 9), false, changes.take(1)),
            ),
        )
    MiloTheme { Surface { WhatsNewContent(state = state, onBack = {}, modifier = Modifier) } }
}
