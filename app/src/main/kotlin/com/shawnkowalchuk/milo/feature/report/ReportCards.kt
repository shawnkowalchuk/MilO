package com.shawnkowalchuk.milo.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChoiceRow
import com.shawnkowalchuk.milo.core.designsystem.component.DateDialog
import com.shawnkowalchuk.milo.core.designsystem.component.FigureRow
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.text.submissionWords
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatDay
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.core.util.formatTenths

// Three of the cards of the Report screen: which period, what a report of it would hold, and
// the reports that were sent. The buttons that make and send one are in ReportActionsCard.kt.

/** Which of the range's two days is being picked. Saved, so turning the phone keeps it open. */
private enum class PickingDay { FIRST, LAST }

/**
 * The period: a whole month, stepped like the Trips screen's, or a date range of two days,
 * each a button that shows the day and opens the calendar. Under the month, whether its report
 * has been sent.
 */
@Composable
internal fun PeriodCard(state: ReportUiState.Ready, actions: ReportActions) {
    val locale = LocalConfiguration.current.locales[0]
    val choice = state.choice
    var picking by rememberSaveable { mutableStateOf<PickingDay?>(null) }

    SectionCard(title = stringResource(R.string.report_period_title)) {
        Column(modifier = Modifier.selectableGroup()) {
            ChoiceRow(
                label = stringResource(R.string.report_period_month),
                selected = choice.kind == PeriodKind.MONTH,
                onSelect = { actions.onKind(PeriodKind.MONTH) },
                supportingText = stringResource(R.string.report_period_month_detail),
            )
            ChoiceRow(
                label = stringResource(R.string.report_period_range),
                selected = choice.kind == PeriodKind.RANGE,
                onSelect = { actions.onKind(PeriodKind.RANGE) },
                supportingText = stringResource(R.string.report_period_range_detail),
            )
        }
        when (choice.kind) {
            PeriodKind.MONTH -> {
                Text(
                    text = formatMonthAndYear(choice.month, locale),
                    style = MaterialTheme.typography.titleMedium,
                )
                val submission = state.submission
                Text(
                    text =
                        submissionWords(
                            firstSentAtMs = submission?.first?.sentAtMs,
                            revisions = submission?.revisions ?: 0,
                            sentAgain = submission?.sentAgain ?: false,
                            latestSentAtMs = submission?.latest?.sentAtMs,
                            zone = state.zone,
                            locale = locale,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // The Trips screen's own words for the same two buttons.
                    TextButton(onClick = actions.onPreviousMonth) {
                        Text(text = stringResource(R.string.trips_previous_month))
                    }
                    // Greyed out on the current month: a month that has not begun has no trips.
                    TextButton(onClick = actions.onNextMonth, enabled = state.canStepForward) {
                        Text(text = stringResource(R.string.trips_next_month))
                    }
                }
            }

            PeriodKind.RANGE -> {
                TextButton(onClick = { picking = PickingDay.FIRST }) {
                    val day = formatDay(choice.rangeFirst, locale)
                    Text(text = stringResource(R.string.report_range_first, day))
                }
                TextButton(onClick = { picking = PickingDay.LAST }) {
                    val day = formatDay(choice.rangeLast, locale)
                    Text(text = stringResource(R.string.report_range_last, day))
                }
            }
        }
    }

    picking?.let { which ->
        val first = which == PickingDay.FIRST
        DateDialog(
            date = if (first) choice.rangeFirst else choice.rangeLast,
            // No day after today: a day that has not come has no trips.
            latest = state.today,
            confirmLabel = stringResource(R.string.action_ok),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { day ->
                picking = null
                if (first) actions.onRangeFirst(day) else actions.onRangeLast(day)
            },
            onDismiss = { picking = null },
        )
    }
}

/**
 * What a report made now would hold: how many Business trips and how far, how many of them
 * carry an asterisk or lack an address, and what of the period is left off. It is here so that
 * nothing on the PDF is a surprise.
 */
@Composable
internal fun SummaryCard(state: ReportUiState.Ready) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = stringResource(R.string.report_summary_title)) {
        val summary = state.summary
        if (summary == null) {
            Quiet(stringResource(R.string.report_reading))
            return@SectionCard
        }
        if (summary.tripCount == 0) {
            Text(
                text = stringResource(R.string.report_summary_none),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            val km = stringResource(R.string.distance_km, formatTenths(summary.tenths, locale))
            Text(
                text = plural(R.plurals.report_summary_trips, summary.tripCount, km),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (summary.markedCount > 0) {
                Quiet(plural(R.plurals.report_summary_marked, summary.markedCount))
            }
            if (summary.withoutAddress > 0) {
                val words = stringResource(R.string.report_pdf_no_address)
                Quiet(plural(R.plurals.report_summary_no_address, summary.withoutAddress, words))
            }
            Quiet(stringResource(R.string.report_summary_rounding))
        }
        if (summary.personalLeftOut > 0) {
            Quiet(plural(R.plurals.report_summary_personal, summary.personalLeftOut))
        }
        if (summary.unsortedLeftOut > 0) {
            Quiet(plural(R.plurals.report_summary_unsorted, summary.unsortedLeftOut))
        }
        if (summary.tripInProgress) Quiet(stringResource(R.string.report_summary_in_progress))
    }
}

/**
 * Every report Shawn has said he sent, newest first: the period, the day, how many trips and
 * how far, and which revision it was. The figures are the ones the report had when it was
 * sent; a trip that was changed since does not change them.
 *
 * Each has a Remove button, for an "I sent it" that was a mistake. The screen asks first.
 *
 * @param onRemove Remove was pressed on the report with this id.
 */
@Composable
internal fun SentReportsCard(state: ReportUiState.Ready, onRemove: (Long) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(title = stringResource(R.string.report_sent_title)) {
        if (state.sent.isEmpty()) Quiet(stringResource(R.string.report_sent_none))
        if (state.problem == ReportProblem.COULD_NOT_REMOVE) {
            val words = stringResource(ReportProblem.COULD_NOT_REMOVE.wordsRes())
            StatusRow(label = words, status = RowStatus.PROBLEM)
        }
        for (line in state.sent) {
            val km = formatKilometres(line.distanceMetres, locale)
            FigureRow(figure = stringResource(R.string.distance_km, km)) {
                Text(
                    text = periodWords(line.period, locale),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val day = formatDate(line.sentAtMs, state.zone, locale)
                val trips = line.tripCount
                Quiet(pluralStringResource(R.plurals.report_sent_line, trips, day, trips))
                if (line.revision > 0) {
                    Quiet(stringResource(R.string.report_sent_revision, line.revision))
                }
            }
            // At the end of a line of its own, under the report it removes.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onRemove(line.id) }, enabled = !state.working) {
                    Text(text = stringResource(R.string.report_remove))
                }
            }
        }
    }
}

/** A sentence whose wording depends on [count], which is also its first argument. */
@Composable
private fun plural(id: Int, count: Int, vararg more: Any): String =
    pluralStringResource(id, count, count, *more)

/** A quieter line: what something means, or what is left out. */
@Composable
internal fun Quiet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
