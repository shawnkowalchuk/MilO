package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Taller than Material's 40 dp button. The main action on a screen (Start trip, Send report) is
// often pressed with the phone in a dashboard mount, where a small target is easy to miss.
private val MinHeight = 56.dp

/**
 * The one main action on a screen. Use it at most once per screen; anything secondary is a text
 * button so the primary action stays obvious.
 *
 * The button is as wide as its text unless the caller passes a width through [modifier].
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(text = text)
    }
}
