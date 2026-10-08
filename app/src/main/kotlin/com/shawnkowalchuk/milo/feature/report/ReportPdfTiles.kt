package com.shawnkowalchuk.milo.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.LinkButton
import com.shawnkowalchuk.milo.core.designsystem.component.PageThumbnail
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowAction
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.text.distanceRes
import com.shawnkowalchuk.milo.core.designsystem.text.unitShortRes
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.data.settings.MissingDetail

// The two tiles in the middle of the Report screen, as the owner's drawing has them: the PDF,
// and who it is sent to.

/**
 * The PDF: a small picture of a page, the file's real name, what the file holds, and "Preview
 * PDF", which makes the PDF if it has to and opens it in the phone's PDF viewer. Beside it,
 * "Export CSV" hands the same trips to the share sheet as a spreadsheet file, as before.
 *
 * Under them stands what this report holds that a reader should know before it is sent, each
 * line only while it applies: trips with an asterisk, trips without an address, and what of
 * the period is left off. It is here so that nothing on the PDF is a surprise.
 */
@Composable
internal fun PdfTile(state: ReportUiState.Ready, actions: ReportActions) {
    val spacing = MiloTheme.spacing
    val idle = !state.working
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.ROOMY,
        gap = spacing.rowGap,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageThumbnail()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.buttonGap),
            ) {
                val title = state.pdfName ?: stringResource(R.string.report_title)
                Text(
                    // The name the file gets, or the report's title while no PDF can be made.
                    text = breakableAfterHyphens(title),
                    // Read out as the name itself, without what was added for the line
                    // breaks, and as a heading a screen reader can jump to.
                    modifier =
                        Modifier.semantics {
                            contentDescription = title
                            heading()
                        },
                    style = MaterialTheme.typography.titleSmall,
                )
                Quiet(
                    stringResource(
                        R.string.report_pdf_contents,
                        stringResource(unitShortRes(state.unit)),
                    ),
                )
                state.pdfPages?.let { pages ->
                    Quiet(pluralStringResource(R.plurals.report_pdf_created, pages, pages))
                }
                // The PDF is made first if there is none of this report: that takes a moment.
                if (state.working) Quiet(stringResource(R.string.report_working))
                // Two links side by side. With a large font the second moves under the first,
                // far enough for each to keep its own place for a finger.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
                ) {
                    LinkButton(
                        text = stringResource(R.string.report_preview_pdf),
                        onClick = actions.onPreviewPdf,
                        enabled = idle,
                    )
                    LinkButton(
                        text = stringResource(R.string.report_export_csv),
                        onClick = actions.onExportCsv,
                        enabled = idle,
                    )
                }
            }
        }
        if (state.problem == ReportProblem.NO_PDF_VIEWER) {
            StatusRow(
                label = stringResource(ReportProblem.NO_PDF_VIEWER.wordsRes()),
                status = RowStatus.PROBLEM,
            )
        }
        state.summary?.let { ReportNotes(it) }
    }
}

/**
 * [fileName] as it is drawn: the same letters, with a place after each hyphen where the line
 * may be broken. A file name has no spaces, and Android breaks a line at none of its hyphens;
 * a name too long for its line was cut in the middle of a word ("Sam-Driv", "er.pdf": seen on
 * an emulator, 2026-10-07). What is added has no width and is not read out.
 */
internal fun breakableAfterHyphens(fileName: String): String =
    fileName.replace(HYPHEN, HYPHEN + ZERO_WIDTH_SPACE)

private const val HYPHEN = "-"
private const val ZERO_WIDTH_SPACE = "\u200B"

/** What a reader should know about this report, a line for each thing that applies. */
@Composable
private fun ReportNotes(summary: ReportSummary) {
    if (summary.tripCount == 0) {
        val locale = LocalConfiguration.current.locales[0]
        val nothing = stringResource(distanceRes(summary.unit), formatTenths(0, locale))
        Quiet(stringResource(R.string.report_summary_none, nothing))
    }
    if (summary.markedCount > 0) {
        Quiet(plural(R.plurals.report_summary_marked, summary.markedCount))
    }
    if (summary.withoutAddress > 0) {
        val words = stringResource(R.string.report_pdf_no_address)
        Quiet(plural(R.plurals.report_summary_no_address, summary.withoutAddress, words))
    }
    if (summary.personalLeftOut > 0) {
        Quiet(plural(R.plurals.report_summary_personal, summary.personalLeftOut))
    }
    if (summary.unsortedLeftOut > 0) {
        Quiet(plural(R.plurals.report_summary_unsorted, summary.unsortedLeftOut))
    }
    if (summary.tripInProgress) Quiet(stringResource(R.string.report_summary_in_progress))
}

/** A sentence whose wording depends on [count], which is also its first argument. */
@Composable
private fun plural(id: Int, count: Int, vararg more: Any): String =
    pluralStringResource(id, count, count, *more)

/**
 * "Sent to": the accountant's address, and under it in grey what the report prints as its
 * sender: the name with the company, and the vehicle. "Change" opens Settings, where all four
 * are typed in.
 *
 * What a report needs and does not have (the name, the address) is said here in red in the
 * place of the missing one, from the start, with the button "Open Settings" under the last of
 * them, so that it is known before a button is pressed. A company or a vehicle that is not set
 * is simply left out, as on the report.
 *
 * @param modifier the screen moves here when a press is refused for a missing setting.
 */
@Composable
internal fun SentToTile(
    state: ReportUiState.Ready,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiloTheme.spacing
    val sender = state.sender
    val toSettings =
        StatusRowAction(
            label = stringResource(R.string.report_open_settings),
            onClick = onOpenSettings,
        )
    Tile(modifier = modifier.fillMaxWidth(), gap = spacing.rowGap) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel(
                stringResource(R.string.report_sent_to),
                Modifier.semantics(mergeDescendants = true) {
                    heading()
                },
            )
            LinkButton(
                text = stringResource(R.string.report_change),
                onClick = onOpenSettings,
                atLineEnd = true,
                pressLabel = stringResource(R.string.report_change_press),
            )
        }
        val address = state.accountantEmail
        if (address != null) {
            Text(text = address, style = MaterialTheme.typography.titleSmall)
        } else {
            // One way to Settings, under the last of the red lines.
            Missing(MissingDetail.ACCOUNTANT_EMAIL, toSettings.takeIf { sender.name != null })
        }
        if (sender.name == null) Missing(MissingDetail.NAME, toSettings)
        val nameAndCompany =
            if (sender.name != null && sender.company != null) {
                stringResource(R.string.report_name_and_company, sender.name, sender.company)
            } else {
                sender.name ?: sender.company
            }
        if (nameAndCompany != null || sender.vehicle != null) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                nameAndCompany?.let { SenderLine(it) }
                sender.vehicle?.let { SenderLine(it) }
            }
        }
    }
}

/** A setting a report cannot do without, said in red, with where it is set. */
@Composable
private fun Missing(detail: MissingDetail, action: StatusRowAction?) {
    StatusRow(
        label =
            stringResource(detail.wordsRes(), stringResource(R.string.settings_report_title)),
        status = RowStatus.PROBLEM,
        action = action,
    )
}

@Composable
private fun SenderLine(text: String) {
    Text(
        text = text,
        style = MiloTheme.textStyles.tileLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The sentence for a setting a report cannot do without. It has one place: where to set it. */
internal fun MissingDetail.wordsRes(): Int = when (this) {
    MissingDetail.NAME -> R.string.report_missing_name
    MissingDetail.ACCOUNTANT_EMAIL -> R.string.report_missing_email
}
