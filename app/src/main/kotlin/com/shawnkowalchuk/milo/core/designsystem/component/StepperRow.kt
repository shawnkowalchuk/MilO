package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * How wide the place kept for the value is, in units of the value's own text size, so that it
 * grows with the phone's font size. The widest value a row shows today ("9.5 min") needs about
 * three and a half; a value that is wider still is shown whole, and only then does the place
 * grow with it.
 */
private const val VALUE_PLACE_EMS = 5

/**
 * One of the two buttons of a [StepperRow].
 *
 * @param description what a screen reader says for the plus or the minus, such as "Wait longer".
 * @param enabled false at the end of the range: the button is greyed out, not hidden, so the
 * row does not jump.
 */
@Immutable
data class StepperButton(
    val description: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * A setting that is a number chosen in steps: its name, what it is for, and the value between a
 * minus and a plus button.
 *
 * Buttons and not a slider, because the values are few and exact (2 or 2.5 minutes), and a
 * slider is hard to set to one of them with a thumb.
 *
 * The value stands in the middle of a place of one fixed width, so the two buttons stay where
 * they are while it changes. A place as wide as its text moved the minus button every time the
 * value went from "2 min" to "1.5 min", and the next press of a thumb that had not moved
 * landed beside it.
 *
 * @param value the value as it is to be read, unit included: "2 min", "0.3 km".
 * @param supportingText a second, quieter line: what the setting does.
 */
@Composable
fun StepperRow(
    label: String,
    value: String,
    decrease: StepperButton,
    increase: StepperButton,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val valueStyle = MaterialTheme.typography.titleMedium
    // The text size is turned into dp first and multiplied after. Android enlarges big text by
    // less than small text, so five times the size, converted, is narrower than five letters.
    val valuePlace = with(LocalDensity.current) { valueStyle.fontSize.toDp() * VALUE_PLACE_EMS }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small),
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = decrease.onClick, enabled = decrease.enabled) {
                Icon(imageVector = MiloIcons.Remove, contentDescription = decrease.description)
            }
            Text(
                text = value,
                style = valueStyle,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .widthIn(min = valuePlace)
                        // A screen reader says the new value after a press, without being asked.
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
            FilledTonalIconButton(onClick = increase.onClick, enabled = increase.enabled) {
                Icon(imageVector = MiloIcons.Add, contentDescription = increase.description)
            }
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun StepperRowPreview() {
    MiloTheme {
        Surface {
            StepperRow(
                label = "Wait after the truck disconnects",
                value = "2 min",
                decrease = StepperButton("Wait less", onClick = {}),
                increase = StepperButton("Wait longer", onClick = {}, enabled = false),
                supportingText = "How long a trip stays open for the truck to reconnect.",
            )
        }
    }
}
