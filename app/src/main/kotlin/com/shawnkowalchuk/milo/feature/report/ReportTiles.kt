package com.shawnkowalchuk.milo.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ChevronIcons
import com.shawnkowalchuk.milo.core.designsystem.component.DateDialog
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.Segment
import com.shawnkowalchuk.milo.core.designsystem.component.SegmentedChoice
import com.shawnkowalchuk.milo.core.designsystem.component.SquareIconButton
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileKind
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.ValueButton
import com.shawnkowalchuk.milo.core.designsystem.text.submissionWords
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatMediumDay
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import com.shawnkowalchuk.milo.core.util.formatTenths

// The first tiles of the Report screen: which period, and under it the two the owner's drawing
// starts with, the Business kilometres and whether the report has been sent. The PDF's tile and
// "Sent to" are in ReportPdfTiles.kt, the buttons in ReportButtons.kt.

/** Which of the range's two days is being picked. Saved, so turning the phone keeps it open. */
private enum class PickingDay { FIRST, LAST }

/**
 * The period, which the drawing does not have and Shawn asked for: a whole month or a date
 * range, chosen with the design's two-part control. Under it the month, with a square button
 * for the month before and one for the month after, like the Trips screen's; or the range's
 * two days, each a button that shows the day and opens the calendar. The last line says what
 * the chosen kind of period means for "submitted".
 */
@Composable
internal fun PeriodTile(state: ReportUiState.Ready, actions: ReportActions) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = MiloTheme.spacing
    val choice = state.choice
    var picking by rememberSaveable { mutableStateOf<PickingDay?>(null) }

    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = spacing.tileGap,
    ) {
        // Lets a screen reader jump from tile to tile, as it could from card to card.
        TileLabel(
            stringResource(R.string.report_period_title),
            Modifier.semantics(mergeDescendants = true) {
                heading()
            },
        )
        // The control Settings uses for "Save as Personal / Ignore them". It is one group for
        // a screen reader by itself: "1 of 2", "2 of 2".
        SegmentedChoice(
            segments =
                listOf(
                    Segment(
                        label = stringResource(R.string.report_period_month),
                        selected = choice.kind == PeriodKind.MONTH,
                        onSelect = { actions.onKind(PeriodKind.MONTH) },
                    ),
                    Segment(
                        label = stringResource(R.string.report_period_range),
                        selected = choice.kind == PeriodKind.RANGE,
                        onSelect = { actions.onKind(PeriodKind.RANGE) },
                    ),
                ),
        )
        when (choice.kind) {
            PeriodKind.MONTH ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatMonthAndYear(choice.month, locale),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // The Trips screen's own words for the same two steps.
                    SquareIconButton(
                        icon = MiloIcons.Back,
                        description = stringResource(R.string.trips_previous_month),
                        onClick = actions.onPreviousMonth,
                        fill = MiloTheme.colors.control.fill,
                    )
                    // Greyed out on the current month: a month that has not begun has no trips.
                    SquareIconButton(
                        icon = ChevronIcons.Forward,
                        description = stringResource(R.string.trips_next_month),
                        onClick = actions.onNextMonth,
                        fill = MiloTheme.colors.control.fill,
                        enabled = state.canStepForward,
                    )
                }

            PeriodKind.RANGE ->
                // Each button has half the line, so the two stay side by side at a large font.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    ValueButton(
                        value = formatMediumDay(choice.rangeFirst, locale),
                        onClick = { picking = PickingDay.FIRST },
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.report_range_from),
                    )
                    ValueButton(
                        value = formatMediumDay(choice.rangeLast, locale),
                        onClick = { picking = PickingDay.LAST },
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.report_range_to),
                    )
                }
        }
        Quiet(
            stringResource(
                when (choice.kind) {
                    PeriodKind.MONTH -> R.string.report_period_month_detail
                    PeriodKind.RANGE -> R.string.report_period_range_detail
                },
            ),
        )
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
 * "Business": the kilometres a report made now would add up to, in the drawing's figure, and
 * how many trips they are. It follows storage, so a trip that ends or is edited while the
 * screen is open changes it. A screen reader reads the tile as one sentence.
 *
 * @param summary null while the period's trips are being read: the figure is then a dash.
 */
@Composable
internal fun BusinessTile(summary: ReportSummary?, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val reading = stringResource(R.string.report_reading)
    Tile(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        padding = TilePadding.EVEN,
    ) {
        TileLabel(stringResource(R.string.trip_business))
        FigureText(
            // A dash while the trips are being read, because a zero would be a figure.
            figure =
                summary?.let { formatTenths(it.tenths, locale) }
                    ?: stringResource(R.string.home_figure_reading),
            unit = stringResource(R.string.unit_km),
            modifier =
                if (summary == null) {
                    Modifier.semantics { contentDescription = reading }
                } else {
                    Modifier
                },
            size = FigureSize.TILE,
        )
        Text(
            text =
                summary?.let {
                    pluralStringResource(R.plurals.trips_count, it.tripCount, it.tripCount)
                } ?: reading,
            style = MiloTheme.textStyles.tileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Status": whether a report for the chosen period is in the list of sent reports. While none
 * is, the tile is the amber one, as drawn, because sending it is still to do; under "Not sent"
 * it says "Reminding daily" only while the monthly reminder really is running for that month.
 * Once one is, the tile is a plain one and says when, in the Trips screen's own sentence.
 */
@Composable
internal fun StatusTile(state: ReportUiState.Ready, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val status = state.status
    val waiting = status is ReportStatus.NotSent
    val quiet =
        if (waiting) {
            MiloTheme.colors.attentionSecondaryText
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val line =
        when (status) {
            is ReportStatus.NotSent ->
                if (status.reminding) stringResource(R.string.report_status_reminding) else null

            is ReportStatus.MonthSent ->
                submissionWords(
                    firstSentAtMs = status.submission.first.sentAtMs,
                    revisions = status.submission.revisions,
                    sentAgain = status.submission.sentAgain,
                    latestSentAtMs = status.submission.latest.sentAtMs,
                    zone = state.zone,
                    locale = locale,
                )

            is ReportStatus.RangeSent ->
                pluralStringResource(
                    R.plurals.report_sent_line,
                    status.tripCount,
                    formatDate(status.sentAtMs, state.zone, locale),
                    status.tripCount,
                )
        }
    Tile(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        kind = if (waiting) TileKind.ATTENTION else TileKind.PLAIN,
        padding = TilePadding.EVEN,
    ) {
        Text(
            text = stringResource(R.string.report_status_label),
            style = MiloTheme.textStyles.tileLabel,
            color = quiet,
        )
        Text(
            text =
                stringResource(
                    if (waiting) R.string.report_status_not_sent else R.string.report_status_sent,
                ),
            style = MiloTheme.textStyles.rowFigure,
        )
        if (line != null) Text(text = line, style = MiloTheme.textStyles.tileLabel, color = quiet)
    }
}
