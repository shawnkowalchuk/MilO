package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The heading of a screen opened from another one, with a quieter line under its title, as the
 * design draws the Report screen: the square back button, then "September report" over "For
 * the accountant".
 *
 * It is `ScreenTitle` with a back button and one line more. A file of its own, because it came
 * with the Report screen's layout while other screens were being laid out at the same time.
 *
 * @param line the quieter line: who or what the screen is for.
 */
@Composable
fun ScreenTitleWithLine(
    text: String,
    line: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareIconButton(
            icon = MiloIcons.Back,
            description = stringResource(R.string.navigate_back),
            onClick = onBack,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                // Lets a screen reader announce where the user has landed.
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = line,
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
