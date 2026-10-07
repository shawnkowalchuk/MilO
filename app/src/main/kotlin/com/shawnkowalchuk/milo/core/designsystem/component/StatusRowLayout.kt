package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// How a StatusRow is laid out when its button may stand at the end of the row, as the design
// draws the rows of the checklist. In a file of its own so that StatusRow.kt stays within the
// size limit (ENGINEERING_STANDARDS section 3).

/**
 * Up to how many lines a row's second line may take beside a button. Longer than that, it is
 * an instruction to be read, and it gets the whole width with the button under it.
 */
internal const val MAX_NOTE_LINES_BESIDE_A_BUTTON = 3

/**
 * Whether a status row's button stands at the end of the row, beside its text, as the design
 * draws it. If not, the buttons stand on a line of their own under the text.
 *
 * Beside the text only when all three hold:
 * - the row has one button. Two side by side leave the text a sliver;
 * - the text keeps at least half of the width it has without the button. A large font, a long
 *   word on the button or a narrow window all take that away, and then the row's name must
 *   not be squeezed into a narrow column;
 * - the second line needs no more than [MAX_NOTE_LINES_BESIDE_A_BUTTON] lines there.
 *
 * @param textWidthAlone how wide the text is with the buttons under it, in pixels.
 * @param textWidthBeside how wide it would be with the button at the end of the row.
 * @param noteLinesBeside how many lines the second line would take at that width; 0 without one.
 */
internal fun buttonStandsBeside(
    buttonCount: Int,
    textWidthAlone: Int,
    textWidthBeside: Int,
    noteLinesBeside: Int,
): Boolean = buttonCount == 1 &&
    textWidthBeside > 0 &&
    textWidthBeside * 2 >= textWidthAlone &&
    noteLinesBeside <= MAX_NOTE_LINES_BESIDE_A_BUTTON

/**
 * The second line of a row whose button may stand beside it: the 12 sp of a note, with the
 * extra room of its taller line taken off above the first line and under the last. One line is
 * then as high as the design draws a row's second line, and a sentence that runs over several
 * lines still has the design's air between them.
 */
internal val checklistNoteStyle: TextStyle
    @Composable
    get() =
        MaterialTheme.typography.bodyMedium.copy(
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
        )

/**
 * A [StatusRow] in the layout of [StatusRowButtons.AT_END]: the button at the end of the row
 * when [buttonStandsBeside] says so, otherwise the buttons under the text, at its end.
 *
 * Either way a button takes up only the 40 dp it is drawn at, so a row is as high as drawn.
 * The place a finger can hit still reaches 4 dp past it, into the room around the row.
 */
@Composable
internal fun StatusRowWithEndButton(
    label: String,
    status: RowStatus,
    supportingText: String?,
    action: StatusRowAction?,
    secondaryAction: StatusRowAction?,
    modifier: Modifier = Modifier,
) {
    val noteStyle = checklistNoteStyle
    val measurer = rememberTextMeasurer()
    val spacing = MiloTheme.spacing
    val buttonCount = listOfNotNull(action, secondaryAction).size
    Layout(
        contents =
            listOf(
                { StatusRowInfo(label, status, supportingText, noteStyle) },
                {
                    if (buttonCount > 0) {
                        StatusRowButtonLine(action, secondaryAction, status)
                    }
                },
            ),
        modifier = modifier,
    ) { (info, buttonLines), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttons = buttonLines.firstOrNull()?.measure(loose)
        val gap = spacing.rowGap.roundToPx()
        // The dot and the room after it, which the text never gets.
        val beforeText = StatusDotSize.roundToPx() + gap

        // How many lines the second line takes when the text is this wide.
        fun noteLines(textWidth: Int): Int = if (supportingText == null || textWidth <= 0) {
            0
        } else {
            measurer
                .measure(supportingText, noteStyle, constraints = Constraints(maxWidth = textWidth))
                .lineCount
        }

        // Without a width to fill there is no end of the row to stand at.
        val widthBesideButton =
            if (buttons != null && constraints.hasBoundedWidth) {
                constraints.maxWidth - gap - buttons.width
            } else {
                null
            }
        val beside =
            widthBesideButton != null &&
                buttonStandsBeside(
                    buttonCount = buttonCount,
                    textWidthAlone = constraints.maxWidth - beforeText,
                    textWidthBeside = widthBesideButton - beforeText,
                    noteLinesBeside = noteLines(widthBesideButton - beforeText),
                )

        if (buttons != null && widthBesideButton != null && beside) {
            val text = info.first().measure(loose.copy(maxWidth = widthBesideButton))
            val height = maxOf(text.height, buttons.height)
            layout(constraints.maxWidth, height) {
                text.placeRelative(0, (height - text.height) / 2)
                buttons.placeRelative(widthBesideButton + gap, (height - buttons.height) / 2)
            }
        } else {
            val text = info.first().measure(loose)
            val width =
                if (constraints.hasBoundedWidth) {
                    constraints.maxWidth
                } else {
                    maxOf(text.width, buttons?.width ?: 0)
                }
            val under = spacing.small.roundToPx()
            val height = text.height + if (buttons == null) 0 else under + buttons.height
            layout(width, height) {
                text.placeRelative(0, 0)
                buttons?.placeRelative(width - buttons.width, text.height + under)
            }
        }
    }
}

/** A row's buttons side by side, the main one last, each as high as it is drawn. */
@Composable
private fun StatusRowButtonLine(
    action: StatusRowAction?,
    secondaryAction: StatusRowAction?,
    status: RowStatus,
) {
    val drawn = Modifier.takesDrawnHeight(ButtonDefaults.MinHeight)
    Row(horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.buttonGap)) {
        if (secondaryAction != null) {
            RowButton(
                text = secondaryAction.label,
                onClick = secondaryAction.onClick,
                modifier = drawn,
            )
        }
        if (action != null) {
            RowButton(
                text = action.label,
                onClick = action.onClick,
                modifier = drawn,
                accent = status == RowStatus.PROBLEM,
            )
        }
    }
}
