package com.shawnkowalchuk.milo.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.PrimaryButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowAction
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.MissingDetail

/**
 * The buttons that make a report: Create PDF (and then Open PDF, to look at it), Send to
 * accountant, which is the screen's main action, and Export CSV.
 *
 * What a report needs and does not have (the name, the accountant's address) is said here from
 * the start, with the way to Settings, so that it is known before a button is pressed. A press
 * that needs one of them makes nothing, and the screen moves to that line.
 */
@Composable
internal fun ActionsCard(state: ReportUiState.Ready, actions: ReportActions) {
    val missingLines = remember { BringIntoViewRequester() }
    // Once for each press that was refused, when its answer is on the screen: the line that
    // says what is missing can be above the button that was pressed. Not when the screen is
    // merely drawn again, as after the phone was turned.
    val refusalsWhenDrawn = remember { state.refusals }
    LaunchedEffect(state.refusals) {
        if (state.refusals != refusalsWhenDrawn) {
            withFrameNanos { }
            missingLines.bringIntoView()
        }
    }

    val idle = !state.working
    SectionCard(title = stringResource(R.string.report_actions_title)) {
        Quiet(stringResource(R.string.report_actions_intro))
        if (state.missing.isNotEmpty()) {
            Column(
                modifier = Modifier.bringIntoViewRequester(missingLines),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
            ) {
                val where = stringResource(R.string.settings_report_title)
                val toSettings =
                    StatusRowAction(
                        label = stringResource(R.string.report_open_settings),
                        onClick = actions.onOpenSettings,
                    )
                state.missing.forEachIndexed { index, missing ->
                    StatusRow(
                        label = stringResource(missing.wordsRes(), where),
                        status = RowStatus.PROBLEM,
                        // One way to Settings, under the last of the lines.
                        action = toSettings.takeIf { index == state.missing.lastIndex },
                    )
                }
            }
        }

        EndButton(stringResource(R.string.report_create_pdf), actions.onCreatePdf, idle)
        state.pdfPages?.let { pages ->
            Quiet(pluralStringResource(R.plurals.report_pdf_created, pages, pages))
            EndButton(stringResource(R.string.report_open_pdf), actions.onOpenPdf, idle)
        }

        PrimaryButton(
            text = stringResource(R.string.report_send),
            onClick = actions.onSend,
            modifier = Modifier.fillMaxWidth(),
            enabled = idle,
        )
        state.accountantEmail?.let { Quiet(stringResource(R.string.report_send_detail, it)) }

        EndButton(stringResource(R.string.report_export_csv), actions.onExportCsv, idle)
        Quiet(stringResource(R.string.report_export_csv_detail))

        if (state.working) Quiet(stringResource(R.string.report_working))
        // A report that could not be removed is said in the list it was to be removed from.
        state.problem?.takeIf { it != ReportProblem.COULD_NOT_REMOVE }?.let {
            StatusRow(label = stringResource(it.wordsRes()), status = RowStatus.PROBLEM)
        }
    }
}

/**
 * A text button at the end of its line, where every secondary action sits. The row takes the
 * card's whole width: a card is only as wide inside as its widest line.
 */
@Composable
private fun EndButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(text = text)
        }
    }
}

/** The sentence for a setting a report cannot do without. It has one place: where to set it. */
internal fun MissingDetail.wordsRes(): Int = when (this) {
    MissingDetail.NAME -> R.string.report_missing_name
    MissingDetail.ACCOUNTANT_EMAIL -> R.string.report_missing_email
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
