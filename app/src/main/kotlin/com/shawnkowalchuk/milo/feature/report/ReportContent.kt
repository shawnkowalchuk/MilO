package com.shawnkowalchuk.milo.feature.report

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePair
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatMonthName

// What the Report screen draws, top to bottom. In a file of its own because ReportScreen.kt,
// which holds the screen's questions and its hand-over to other apps, would be over the size
// limit with it (ENGINEERING_STANDARDS section 3).

/**
 * The Report screen as the owner's design draws it: the title over "For the accountant" (since
 * 2026-10-07 at the end of the top line every screen has, as the way back), the two tiles for
 * the Business kilometres and the status, the PDF, who it is sent to, the main button with two
 * quiet ones under it, and the sentence that says what sending does. Two things the drawing
 * does not have stand in the same style: the period's tile, directly under the title, and the
 * list of sent reports at the end.
 */
@Composable
internal fun ReportContent(
    state: ReportUiState,
    actions: ReportActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val spacing = MiloTheme.spacing
    val ready = state as? ReportUiState.Ready
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.small),
    ) {
        AppHeader(
            // The month by its name, as drawn; a date range has no short name of its own.
            title =
                if (ready != null && ready.choice.kind == PeriodKind.MONTH) {
                    val month = formatMonthName(ready.choice.month, locale)
                    stringResource(R.string.report_month_title, month)
                } else {
                    stringResource(R.string.report_title)
                },
            onBack = onBack,
            line = stringResource(R.string.report_for_accountant),
            // As on Home: with the gap between two tiles, the design's 16 under the top line.
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        when (state) {
            ReportUiState.Reading ->
                Tile(modifier = Modifier.fillMaxWidth()) {
                    Quiet(stringResource(R.string.report_reading))
                }

            ReportUiState.Unreadable ->
                Tile(modifier = Modifier.fillMaxWidth()) {
                    StatusRow(
                        label = stringResource(R.string.report_settings_unreadable),
                        status = RowStatus.PROBLEM,
                    )
                }

            is ReportUiState.Ready -> ReadyTiles(state, actions)
        }
    }
}

@Composable
private fun ColumnScope.ReadyTiles(state: ReportUiState.Ready, actions: ReportActions) {
    val sentTo = remember { BringIntoViewRequester() }
    // Once for each press that was refused, when its answer is on the screen: the line that
    // says what is missing can be above the button that was pressed. Not when the screen is
    // merely drawn again, as after the phone was turned.
    val refusalsWhenDrawn = remember { state.refusals }
    LaunchedEffect(state.refusals) {
        if (state.refusals != refusalsWhenDrawn) {
            withFrameNanos { }
            sentTo.bringIntoView()
        }
    }

    PeriodTile(state, actions)
    TilePair(
        first = { half -> BusinessTile(state.summary, half) },
        second = { half -> StatusTile(state, half) },
    )
    PdfTile(state, actions)
    SentToTile(state, actions.onOpenSettings, Modifier.bringIntoViewRequester(sentTo))
    SendButtons(state, actions)
    SentReports(state, actions.onRemoveSent)
}

/** A quieter line: what something means, or what is left out. */
@Composable
internal fun Quiet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
