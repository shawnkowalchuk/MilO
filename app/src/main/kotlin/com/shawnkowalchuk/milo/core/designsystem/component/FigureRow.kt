package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * A line or two of text with one figure at its end: a trip and its kilometres. Every list of
 * trips is made of these, so a trip looks the same on Home as on the Trips screen.
 *
 * @param figure the figure as it is to be read, unit included ("12.4 km"), or null to show
 * none, when a wrong figure would be worse than no figure.
 * @param counted false greys the figure, so that one that is in no total, or not in the total
 * the row stands beside, does not read as one that is.
 * @param figureSpoken what a screen reader says for the figure, where the design draws the
 * number without its unit ("18.6") and the unit has to be said all the same. Null to have the
 * figure read as it is written.
 * @param content what stands before the figure: the trip's times, and whatever belongs under
 * them.
 */
@Composable
fun FigureRow(
    figure: String?,
    modifier: Modifier = Modifier,
    counted: Boolean = true,
    figureSpoken: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
            content = content,
        )
        if (figure != null) {
            Text(
                text = figure,
                modifier =
                    if (figureSpoken == null) {
                        Modifier
                    } else {
                        Modifier.semantics { contentDescription = figureSpoken }
                    },
                // A step larger than the row's own text, as the design sets a row's figure.
                style = MaterialTheme.typography.titleSmall,
                color =
                    if (counted) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
