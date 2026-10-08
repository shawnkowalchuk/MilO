package com.shawnkowalchuk.milo.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.GroupLabel
import com.shawnkowalchuk.milo.core.designsystem.component.PrimaryIconButton
import com.shawnkowalchuk.milo.core.designsystem.component.ReportIcons
import com.shawnkowalchuk.milo.core.designsystem.component.RowButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileRow
import com.shawnkowalchuk.milo.core.designsystem.component.WideButton
import com.shawnkowalchuk.milo.core.designsystem.component.tileRowPlace
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.designsystem.text.distanceSpokenRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatDistance

// The lower part of the Report screen: the buttons, as the owner's drawing has them, and under
// them the list of the reports that were sent, which the drawing does not have.

/**
 * The screen's main button, "Email the report"; under it, side by side, "Save PDF and CSV" and
 * "Mark as sent"; and the sentence that says what the main button does.
 *
 * While a file is being made the buttons are greyed and a line says so. A press that did not
 * do what it said is answered in red under the sentence until the next press, and the screen
 * moves to that answer: the press may have been made further up.
 */
@Composable
internal fun SendButtons(state: ReportUiState.Ready, actions: ReportActions) {
    val spacing = MiloTheme.spacing
    val idle = !state.working
    // The failure of the last press, where this part of the screen is the one to say it. No
    // PDF viewer is said in the PDF's tile, and a report that could not be removed in the list.
    val problem =
        state.problem?.takeIf {
            it != ReportProblem.NO_PDF_VIEWER && it != ReportProblem.COULD_NOT_REMOVE
        }
    val answer = remember { BringIntoViewRequester() }
    val problemWhenDrawn = remember { problem }
    LaunchedEffect(problem) {
        // Not when the screen is merely drawn again with the answer already on it.
        if (problem != null && problem != problemWhenDrawn) {
            withFrameNanos { }
            answer.bringIntoView()
        }
    }

    PrimaryIconButton(
        text = stringResource(R.string.report_email),
        icon = ReportIcons.Send,
        onClick = actions.onSend,
        modifier = Modifier.fillMaxWidth(),
        enabled = idle,
    )
    Row(
        // The line is as high as its higher button needs, and both fill it.
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(spacing.tileGap),
    ) {
        WideButton(
            text = stringResource(R.string.report_save_both),
            onClick = actions.onSaveBoth,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = idle,
        )
        // Nothing true can be marked while the period's trips are still being read.
        WideButton(
            text = stringResource(R.string.report_mark_sent),
            onClick = actions.onMarkSent,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enabled = idle && state.summary != null,
        )
    }
    Column(
        // In from the edge like a label above a tile, as drawn.
        modifier = Modifier.padding(horizontal = spacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Quiet(stringResource(R.string.report_email_note))
        if (state.working) Quiet(stringResource(R.string.report_working))
    }
    if (problem != null) {
        Tile(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(answer)) {
            StatusRow(label = stringResource(problem.wordsRes()), status = RowStatus.PROBLEM)
        }
    }
}

/**
 * Every report Shawn has said he sent, newest first, as the rows of one tile: the period, the
 * day, how many trips, which revision it was, and how far. The figures are the ones the report
 * had when it was sent; a trip that was changed since does not change them.
 *
 * Each has a Remove button, for a report that was recorded by mistake. The screen asks first.
 *
 * @param onRemove Remove was pressed on the report with this id.
 */
@Composable
internal fun SentReports(state: ReportUiState.Ready, onRemove: (Long) -> Unit) {
    GroupLabel(stringResource(R.string.report_sent_title))
    if (state.problem == ReportProblem.COULD_NOT_REMOVE) {
        Tile(modifier = Modifier.fillMaxWidth()) {
            val words = stringResource(ReportProblem.COULD_NOT_REMOVE.wordsRes())
            StatusRow(label = words, status = RowStatus.PROBLEM)
        }
    }
    if (state.sent.isEmpty()) {
        Tile(modifier = Modifier.fillMaxWidth()) {
            Quiet(stringResource(R.string.report_sent_none))
        }
        return
    }
    Column {
        state.sent.forEachIndexed { index, line ->
            // A row keeps what belongs to it when the one above it is removed.
            key(line.id) {
                TileRow(place = tileRowPlace(index, state.sent.size)) {
                    SentRow(line, state, onRemove = { onRemove(line.id) })
                }
            }
        }
    }
}

@Composable
private fun SentRow(line: SentLine, state: ReportUiState.Ready, onRemove: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = MiloTheme.spacing
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.rowGap),
        // From the top, so that the period and its kilometres stand on one line.
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.textGap),
        ) {
            Text(
                text = periodWords(line.period, locale),
                style = MaterialTheme.typography.bodyLarge,
            )
            val day = formatDate(line.sentAtMs, state.zone, locale)
            val trips = line.tripCount
            Text(
                text = pluralStringResource(R.plurals.report_sent_line, trips, day, trips),
                style = MiloTheme.textStyles.tileLabel,
                color = quiet,
            )
            if (line.revision > 0) {
                Text(
                    text = stringResource(R.string.report_sent_revision, line.revision),
                    style = MiloTheme.textStyles.tileLabel,
                    color = quiet,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.textGap),
        ) {
            // In the unit the report was printed in, as it stood on the report.
            val total = formatDistance(line.distanceMetres, line.unit, locale)
            val spoken = stringResource(distanceSpokenRes(line.unit), total)
            Text(
                text = stringResource(distanceRes(line.unit), total),
                // A screen reader is read the unit's whole word.
                modifier = Modifier.semantics { contentDescription = spoken },
                style = MaterialTheme.typography.titleSmall,
            )
            RowButton(
                text = stringResource(R.string.report_remove),
                onClick = onRemove,
                enabled = !state.working,
            )
        }
    }
}

/** The sentence for a press that did not do what it said. */
internal fun ReportProblem.wordsRes(): Int = when (this) {
    ReportProblem.COULD_NOT_CREATE -> R.string.report_problem_create
    ReportProblem.NO_EMAIL_APP -> R.string.report_problem_no_email_app
    ReportProblem.NO_PDF_VIEWER -> R.string.report_problem_no_pdf_viewer
    ReportProblem.NO_SHARE_APP -> R.string.report_problem_no_share_app
    ReportProblem.COULD_NOT_RECORD -> R.string.report_problem_record
    ReportProblem.COULD_NOT_REMOVE -> R.string.report_problem_remove
}
