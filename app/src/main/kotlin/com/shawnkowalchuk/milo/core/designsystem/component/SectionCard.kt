package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * A titled group of related content: the standard way to divide a screen into sections.
 *
 * It always fills the available width so that stacked cards share the same edges. [content] is
 * laid out in a column with the standard gap between items, so callers do not add their own
 * spacing between rows.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MiloTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                // Lets a screen reader jump from section to section instead of reading every row.
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}
