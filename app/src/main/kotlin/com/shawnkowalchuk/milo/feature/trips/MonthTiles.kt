package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.FigureSize
import com.shawnkowalchuk.milo.core.designsystem.component.FigureText
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import com.shawnkowalchuk.milo.core.designsystem.component.PillButton
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.SmallLinkTile
import com.shawnkowalchuk.milo.core.designsystem.component.SmallTile
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRowAction
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileKind
import com.shawnkowalchuk.milo.core.designsystem.component.TileLabel
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.text.submissionWords
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatTenths
import com.shawnkowalchuk.milo.data.report.ChangedSinceSent
import com.shawnkowalchuk.milo.data.report.MonthSubmission
import com.shawnkowalchuk.milo.data.trip.CategoryTotals
import java.time.ZoneId
import java.util.Locale

// The tiles at the top of the Trips screen, as the owner's drawing has them: the month's own
// accent tile, and under it "Personal" and "Add missed trip" side by side.

/**
 * The month on screen: its name, the kilometres of its Business trips in the large figures,
 * how many they are, and the pill that says whether the month's report has been sent. **The
 * pill is the way to the Report screen for this month.**
 *
 * Business alone stands here, because that is what the month's report is made of. Personal has
 * a tile of its own, so the two can never be read as one total. The figure is added up as the
 * report adds up (`sumOfTenths`), so it is the total that report prints.
 *
 * A month whose report was sent says when, and when it was sent again, in a line of its own
 * under the figures: a pill holds two words, not a date.
 *
 * @param summary null while the month's trips are being read: the figure is then a dash.
 * @param submission that the month's report was sent, and when; null while it was not.
 */
@Composable
internal fun MonthTile(
    monthName: String,
    summary: MonthSummary?,
    submission: MonthSubmission?,
    zone: ZoneId,
    onOpenReport: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = MiloTheme.spacing
    val business = summary?.totals?.business
    val reading = stringResource(R.string.trips_reading)
    Tile(
        modifier = Modifier.fillMaxWidth(),
        kind = TileKind.ACCENT,
        padding = TilePadding.ROOMY,
        gap = spacing.small,
    ) {
        // Side by side, as drawn: the figures at the start, the pill at the end and on the
        // lowest line. With a large font the pill moves under the figures rather than
        // squeezing them.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                Text(
                    text = stringResource(R.string.trips_month_business, monthName),
                    // Lets a screen reader jump to the month's figures.
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                )
                FigureText(
                    // A dash while the trips are being read, because a zero would be a figure.
                    figure =
                        business?.let { formatTenths(it.tenths, locale) }
                            ?: stringResource(R.string.home_figure_reading),
                    unit = stringResource(R.string.unit_km),
                    modifier =
                        if (business == null) {
                            Modifier.semantics { contentDescription = reading }
                        } else {
                            Modifier
                        },
                    size = FigureSize.TOTAL,
                )
                Text(
                    text = business?.let { trips(it.count) } ?: reading,
                    style = MiloTheme.textStyles.accentNote,
                )
            }
            val state = stringResource(pillWordsRes(submission))
            PillButton(
                text = state,
                spokenName = stringResource(R.string.trips_pill_spoken, state, monthName),
                pressLabel = stringResource(R.string.trips_pill_press),
                onClick = onOpenReport,
            )
        }
        if (submission != null) {
            Text(
                text =
                    submissionWords(
                        firstSentAtMs = submission.first.sentAtMs,
                        revisions = submission.revisions,
                        sentAgain = submission.sentAgain,
                        latestSentAtMs = submission.latest.sentAtMs,
                        zone = zone,
                        locale = locale,
                    ),
                style = MiloTheme.textStyles.accentNote,
            )
        }
    }
}

/**
 * The month's Personal trips, apart from Business: their kilometres and how many they are
 * ("22.0 km · 3"). A screen reader is read the whole of it: "Personal: 22.0 km · 3 trips".
 *
 * Trips that are not sorted yet have a small line under it, only while there is such a trip,
 * which in ordinary use is never.
 *
 * @param totals null while the month's trips are being read: the value is then a dash.
 */
@Composable
internal fun PersonalTile(totals: CategoryTotals?, locale: Locale, modifier: Modifier) {
    val reading = stringResource(R.string.trips_reading)
    val personal = totals?.personal
    val spoken =
        personal?.let {
            pluralStringResource(
                R.plurals.trips_personal_total,
                it.count,
                it.count,
                kilometres(it.tenths, locale),
            )
        } ?: reading
    SmallTile(modifier = modifier) {
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
        ) {
            TileLabel(stringResource(R.string.trip_personal))
            Text(
                text =
                    personal?.let {
                        stringResource(
                            R.string.trips_personal_value,
                            kilometres(it.tenths, locale),
                            it.count,
                        )
                    } ?: stringResource(R.string.home_figure_reading),
                style = MiloTheme.textStyles.appName,
            )
        }
        val unsorted = totals?.unsorted
        if (unsorted != null && unsorted.count > 0) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.trips_unsorted_total,
                        unsorted.count,
                        unsorted.count,
                        kilometres(unsorted.tenths, locale),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The way to type in a trip MilO missed. The whole tile is the button. */
@Composable
internal fun AddTripTile(onAdd: () -> Unit, modifier: Modifier) {
    SmallLinkTile(
        icon = MiloIcons.Add,
        text = stringResource(R.string.trips_action_add),
        onClick = onAdd,
        modifier = modifier,
    )
}

/**
 * That the month's Business trips are not what the report that was sent held, with both sets
 * of figures, so that Shawn can tell whether a revision is needed, and the button that leads
 * to the Report screen, where one is sent.
 */
@Composable
internal fun ChangedSinceTile(changed: ChangedSinceSent, locale: Locale, onOpenReport: () -> Unit) {
    Tile(modifier = Modifier.fillMaxWidth()) {
        StatusRow(
            label =
                stringResource(
                    R.string.trips_changed_since_sent,
                    trips(changed.sentTripCount),
                    kilometres(changed.sentTenths, locale),
                    trips(changed.tripCount),
                    kilometres(changed.tenths, locale),
                ),
            status = RowStatus.PROBLEM,
            supportingText = stringResource(R.string.trips_changed_since_sent_detail),
            action =
                StatusRowAction(
                    label = stringResource(R.string.trips_action_report),
                    onClick = onOpenReport,
                ),
        )
    }
}

@Composable
private fun trips(count: Int): String = pluralStringResource(R.plurals.trips_count, count, count)

/** A total as it is printed, from tenths of a kilometre: "412.3 km". */
@Composable
private fun kilometres(tenths: Long, locale: Locale): String =
    stringResource(R.string.distance_km, formatTenths(tenths, locale))
