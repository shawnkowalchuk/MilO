package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws one of these buttons. It grows when a large font needs more. */
private val ButtonHeight = 48.dp

/** The design's room between two of them: seven have to fit across a tile. */
private val ButtonGap = 5.dp

/**
 * The smallest a button's word is written. Seven buttons share a line, so each is narrow, and
 * at the phone's largest font sizes a word like "Wed" is wider than its button: it is then
 * written smaller, down to this, and not cut off.
 */
private val SmallestWord = 8.sp

/**
 * One button of a [ToggleButtonRow].
 *
 * @param label the short word on the button: "Mon".
 * @param spokenName what a screen reader says the button is: "Monday".
 * @param on whether it is switched on.
 */
@Immutable
data class ToggleButton(
    val label: String,
    val spokenName: String,
    val on: Boolean,
    val onToggle: (Boolean) -> Unit,
)

/**
 * A row of buttons that are each on or off by themselves, side by side and equally wide, as
 * the design draws the seven days of the work schedule. One that is on is the accent with dark
 * words; one that is off is the quiet fill of a control with grey words.
 *
 * Any number of them may be on, none included: this is not a choice of one (`SegmentedChoice`,
 * `ChipChoice`).
 *
 * Each is drawn 48 dp high. Seven across a tile are a little narrower than Android's 48 dp, and
 * Compose lets a press that lands within that distance of a smaller control count as a press
 * on it; between two of them the nearer one gets it.
 *
 * The colour is never the only sign for a screen reader: each button is read with its full
 * name and with [onWords] or [offWords].
 *
 * @param onWords what a screen reader says for a button that is on: "Work day".
 * @param offWords and for one that is off: "Not a work day".
 */
@Composable
fun ToggleButtonRow(
    buttons: List<ToggleButton>,
    onWords: String,
    offWords: String,
    modifier: Modifier = Modifier,
) {
    Row(
        // The row is as high as its highest button needs, and every button fills it.
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        for (button in buttons) {
            val scheme = MaterialTheme.colorScheme
            val fill by animateColorAsState(
                targetValue = if (button.on) scheme.primary else MiloTheme.colors.control.fill,
                label = "toggle button fill",
            )
            val words by animateColorAsState(
                targetValue = if (button.on) scheme.onPrimary else scheme.onSurfaceVariant,
                label = "toggle button words",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // The clip keeps the ripple of a press inside the button.
                        .clip(MiloTheme.shapes.control)
                        .background(fill)
                        .toggleable(
                            value = button.on,
                            role = Role.Switch,
                            onValueChange = button.onToggle,
                        ).semantics {
                            contentDescription = button.spokenName
                            stateDescription = if (button.on) onWords else offWords
                        }.heightIn(min = ButtonHeight),
                contentAlignment = Alignment.Center,
            ) {
                val style = MaterialTheme.typography.labelMedium
                Text(
                    text = button.label,
                    // The full name is read out, above; the short word is for the eye.
                    modifier = Modifier.clearAndSetSemantics {},
                    style = style,
                    color = words,
                    maxLines = 1,
                    autoSize =
                        TextAutoSize.StepBased(
                            minFontSize = SmallestWord,
                            maxFontSize = style.fontSize,
                        ),
                )
            }
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun ToggleButtonRowPreview() {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    MiloTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            ToggleButtonRow(
                buttons = days.mapIndexed { index, day -> ToggleButton(day, day, index < 5) {} },
                onWords = "Work day",
                offWords = "Not a work day",
            )
        }
    }
}
