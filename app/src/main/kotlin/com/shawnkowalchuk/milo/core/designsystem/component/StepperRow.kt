package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * How wide the place kept for the value is, in units of the value's own text size, so that it
 * grows with the phone's font size. The design draws the place 72 dp wide for text of 16, which
 * is four and a half. The widest value a row shows today ("9.5 min", "Day 31") needs about
 * three and a half; a value that is wider still is shown whole, and only then does the place
 * grow with it.
 */
private const val VALUE_PLACE_EMS = 4.5f

/** Beside the buttons the name has at least one part in this many of the row's width. */
private const val LEAST_SHARE_FOR_NAME = 3

/** How high the design draws the row: the height of its two square buttons. */
private val RowHeight = 44.dp

/**
 * Each square is drawn 44 dp and takes up 48, for the finger. Moved out by the 2 dp the last one
 * keeps free at its end, its drawn edge lines up with the edge of whatever the row stands in.
 */
private val ButtonOverhang = 2.dp

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
 * A setting that is a number chosen in steps, as the design draws it: its name at the start of
 * the row, and at the end the value between a minus and a plus button, each a square the colour
 * of a control on a tile.
 *
 * Buttons and not a slider, because the values are few and exact (2 or 2.5 minutes), and a
 * slider is hard to set to one of them with a thumb.
 *
 * The value stands in the middle of a place of one fixed width, so the two buttons stay where
 * they are while it changes. A place as wide as its text moved the minus button every time the
 * value went from "2 min" to "1.5 min", and the next press of a thumb that had not moved
 * landed beside it.
 *
 * The row is drawn 44 dp high, as its squares are, and takes up no more while its name fits
 * beside them: the 48 dp a finger can hit reach 2 dp past it, above and below. A name that
 * needs more lines makes the row as high as it needs. At a font size so large that the room
 * beside the buttons is narrower than the name's longest word, or than a third of the row, the
 * name stands above them, so that no word is broken in two.
 *
 * @param value the value as it is to be read, unit included: "2 min", "0.3 km".
 * @param supportingText a second, quieter line under the row: what the setting does.
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
    // less than small text, so four and a half times the size, converted, is narrower than
    // that many letters.
    val valuePlace = with(LocalDensity.current) { valueStyle.fontSize.toDp() * VALUE_PLACE_EMS }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
    ) {
        NameBesideButtons(
            modifier = Modifier.fillMaxWidth().takesUpOnly(RowHeight),
            gap = MiloTheme.spacing.rowGap,
        ) {
            Text(text = label, style = MiloTheme.textStyles.sentence)
            Row(
                modifier = Modifier.offset(x = ButtonOverhang),
                // With the 2 dp each square keeps free around itself, the design's 6 dp.
                horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SquareIconButton(
                    icon = MiloIcons.Remove,
                    description = decrease.description,
                    onClick = decrease.onClick,
                    fill = MiloTheme.colors.control.fill,
                    enabled = decrease.enabled,
                )
                Text(
                    text = value,
                    style = valueStyle,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .widthIn(min = valuePlace)
                            // A screen reader says the new value after a press, without being
                            // asked.
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
                SquareIconButton(
                    icon = MiloIcons.Add,
                    description = increase.description,
                    onClick = increase.onClick,
                    fill = MiloTheme.colors.control.fill,
                    enabled = increase.enabled,
                )
            }
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A name at the start of a line and a group of buttons at its end, each in the middle of the
 * line's height. If the room beside the buttons is narrower than the name's longest word, or
 * than a third of the line, the name takes a line of its own above them: beside the buttons it
 * would be a column of single words.
 *
 * @param content exactly two things: the name, then the buttons.
 */
@Composable
private fun NameBesideButtons(modifier: Modifier, gap: Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val (name, buttons) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placedButtons = buttons.measure(loose)
        val beside = constraints.maxWidth - placedButtons.width - gap.roundToPx()
        val needed =
            maxOf(
                name.minIntrinsicWidth(Constraints.Infinity),
                constraints.maxWidth / LEAST_SHARE_FOR_NAME,
            )
        if (beside >= needed) {
            val placedName = name.measure(loose.copy(maxWidth = beside))
            val height = maxOf(placedName.height, placedButtons.height)
            layout(constraints.maxWidth, height) {
                placedName.placeRelative(0, (height - placedName.height) / 2)
                placedButtons.placeRelative(
                    constraints.maxWidth - placedButtons.width,
                    (height - placedButtons.height) / 2,
                )
            }
        } else {
            val placedName = name.measure(loose)
            layout(constraints.maxWidth, placedName.height + placedButtons.height) {
                placedName.placeRelative(0, 0)
                placedButtons.placeRelative(
                    constraints.maxWidth - placedButtons.width,
                    placedName.height,
                )
            }
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun StepperRowPreview() {
    MiloTheme {
        // On a tile, where a stepper stands in the app: its buttons are drawn to be seen there.
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
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
