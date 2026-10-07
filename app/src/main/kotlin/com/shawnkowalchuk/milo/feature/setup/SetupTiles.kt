package com.shawnkowalchuk.milo.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.GroupLabel
import com.shawnkowalchuk.milo.core.designsystem.component.ProgressLine
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowAction
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowButtons
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLinkLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.component.TileRow
import com.shawnkowalchuk.milo.core.designsystem.component.tileRowPlace
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.system.SetupDetail
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SetupItem
import com.shawnkowalchuk.milo.platform.system.SetupRow
import java.time.ZoneId

// Setup's tiles, in the order of the owner's drawing: the count, then one tile for each group
// of rows.

/** What the rows of the checklist can ask the screen to do. */
internal class SetupActions(
    val onFix: (SetupFix) -> Unit,
    val onConfirm: (ConfirmedStep) -> Unit,
    val onTakeBack: (ConfirmedStep) -> Unit,
)

/**
 * The tile at the top: how many rows are ready out of how many, what is the matter with the
 * others, and a bar that is filled by the share that is ready. A screen reader reads it as one
 * sentence; the bar says nothing the words do not.
 *
 * The same tile is the way into Setup from Settings ([SetupLinkTile]): given a [link], it has
 * the screen's name and an arrowhead on its first line, the whole tile opens the checklist, and
 * "2 to fix" is red, since the rows that say what is wrong are not on that screen.
 *
 * @param summary null while the phone is being read for the first time. Only Settings shows
 * the tile then: Setup itself has one tile that says so in place of all of them.
 */
@Composable
internal fun SummaryTile(summary: SetupSummary?, link: TilePress? = null) {
    Tile(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        padding = TilePadding.ROOMY,
        press = link,
        gap = MiloTheme.spacing.rowGap,
    ) {
        if (link != null) TileLinkLabel(stringResource(R.string.setup_title))
        if (summary == null) {
            Text(
                text = stringResource(R.string.setup_reading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Tile
        }
        val toFixIsRed = link != null && summary.toFix > 0
        // Side by side, as drawn, while both fit. With a large font the words move under the
        // count instead of being squeezed beside it.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        ) {
            FigureText(
                figure = summary.ready.toString(),
                unit =
                    pluralStringResource(
                        R.plurals.setup_summary_of_ready,
                        summary.total,
                        summary.total,
                    ),
                modifier = Modifier.align(Alignment.Bottom),
                size = FigureSize.COUNT,
            )
            Text(
                text = summaryWords(summary),
                modifier = Modifier.align(Alignment.Bottom),
                style = MiloTheme.textStyles.tileLabel,
                color =
                    if (toFixIsRed) {
                        MiloTheme.statusColors.problem
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        ProgressLine(fraction = summary.readyShare, thick = true)
    }
}

/** "2 to fix · 1 to confirm": each count that is not zero, or that there is nothing to do. */
@Composable
private fun summaryWords(summary: SetupSummary): String {
    if (summary.allReady) return stringResource(R.string.setup_summary_all_ready)
    val counts =
        listOf(
            R.plurals.setup_summary_to_fix to summary.toFix,
            R.plurals.setup_summary_to_confirm to summary.toConfirm,
            R.plurals.setup_summary_not_checked to summary.notChecked,
        )
    val parts = mutableListOf<String>()
    for ((words, count) in counts) {
        if (count > 0) parts += pluralStringResource(words, count, count)
    }
    return parts.joinToString(stringResource(R.string.setup_summary_separator))
}

/**
 * A group of rows as the design draws it: a small label on the page, and one tile of rows.
 *
 * @param rows in the order they are listed.
 */
@Composable
internal fun ChecklistGroup(label: String, rows: List<SetupRow>, actions: SetupActions) {
    GroupLabel(label)
    Column {
        rows.forEachIndexed { index, row ->
            // A row moves when it is fixed. The key lets it take what it is with it.
            key(row.item) {
                TileRow(place = tileRowPlace(index, rows.size)) { ChecklistRow(row, actions) }
            }
        }
    }
}

@Composable
private fun ChecklistRow(row: SetupRow, actions: SetupActions) {
    val fix = row.fix
    val fixButton =
        fix?.let { StatusRowAction(stringResource(row.fixLabelRes())) { actions.onFix(it) } }
    val step = row.confirmStep
    // A row Shawn can confirm has two buttons: the one that opens the setting, and the one that
    // says he set it (or takes that back). The button for the next thing to do comes last.
    val (main, second) =
        when {
            step == null -> fixButton to null

            row.confirmedAtMs == null ->
                StatusRowAction(stringResource(R.string.setup_action_confirm)) {
                    actions.onConfirm(step)
                } to fixButton

            else -> {
                val takeBack =
                    StatusRowAction(stringResource(R.string.setup_action_take_back)) {
                        actions.onTakeBack(step)
                    }
                if (fixButton == null) takeBack to null else fixButton to takeBack
            }
        }
    StatusRow(
        label = stringResource(row.item.labelRes()),
        status = row.state.asRowStatus(),
        supportingText = detailText(row),
        action = main,
        secondaryAction = second,
        // One button stands at the end of the row, as drawn, when the row's words leave room.
        buttons = StatusRowButtons.AT_END,
    )
}

/** The sentence under the row's name, with the truck's name or the confirmation date filled in. */
@Composable
private fun detailText(row: SetupRow): String {
    val confirmedAtMs = row.confirmedAtMs
    return when {
        row.detail == SetupDetail.CONFIRMED && confirmedAtMs != null -> {
            val locale = LocalConfiguration.current.locales[0]
            val day = formatDate(confirmedAtMs, ZoneId.systemDefault(), locale)
            stringResource(row.detailRes(), day)
        }

        row.item == SetupItem.TRUCK && row.detail in TRUCK_DETAILS_WITH_A_NAME ->
            stringResource(
                row.detailRes(),
                row.truckName ?: stringResource(R.string.truck_without_a_name),
            )

        else -> stringResource(row.detailRes())
    }
}

/** The truck row's sentences that name the truck. */
private val TRUCK_DETAILS_WITH_A_NAME =
    setOf(SetupDetail.FINE, SetupDetail.TRUCK_ASSOCIATION_MISSING, SetupDetail.TRUCK_NOT_WATCHED)
