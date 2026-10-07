package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The design draws several controls lower than Android's smallest target for a finger: a pill
// is 30 dp high, a button under a trip 44, a switch 28. Each is drawn as it is drawn, and the
// place a finger can hit is 48 dp all the same. The two parts below are how: neither makes the
// control take up more room than it is drawn in, so a tile stays as high as the design has it.

/** Android's smallest target for a finger. */
private val FingerTarget = 48.dp

/**
 * What is pressed, laid over what is drawn. It is as large as the box it stands in, and where
 * that is lower or narrower than a finger needs, it reaches past the box by the same amount on
 * both sides. It draws nothing: the ripple of a press is drawn by whatever is handed the same
 * [interactionSource].
 *
 * The room it reaches into has to be free: nothing else that can be pressed may stand within
 * 9 dp of a pill, or within 2 dp of a 44 dp button.
 *
 * @param spokenName what a screen reader says the button is. What is drawn under it should
 * say nothing itself, or the words are read twice.
 * @param pressLabel what a screen reader says the press does, after "double tap to".
 */
@Composable
internal fun BoxScope.PressArea(
    spokenName: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    pressLabel: String? = null,
) {
    Spacer(
        modifier =
            Modifier
                .matchParentSize()
                .reachingAFingerTarget()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = pressLabel,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = spokenName },
    )
}

/**
 * Measures what it is put on at least as large as a finger needs, and still reports the size
 * it was given, with the larger thing centred on it.
 */
private fun Modifier.reachingAFingerTarget(): Modifier = layout { measurable, constraints ->
    if (constraints.hasFixedWidth && constraints.hasFixedHeight) {
        // `matchParentSize` hands over the box's own size, as the only size allowed.
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val target = FingerTarget.roundToPx()
        val pressWidth = maxOf(width, target)
        val pressHeight = maxOf(height, target)
        val placeable = measurable.measure(Constraints.fixed(pressWidth, pressHeight))
        layout(width, height) {
            placeable.place((width - pressWidth) / 2, (height - pressHeight) / 2)
        }
    } else {
        // Asked how much room it would like, before the box has a size: a row that makes
        // its buttons equally high asks every one of them. It wants none of its own.
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * Lets a row or a control that keeps 48 dp for a finger take up only the height it is drawn at,
 * so that what it stands in is as high as drawn. The place a finger can hit is not made
 * smaller: it reaches past what is drawn, half above and half below. So only use it where that
 * much room is kept free: the padding of a tile, or the gap to a neighbour.
 *
 * One that needs more than the 48 dp, because its words run onto a second line or the font is
 * very large, is left as high as it is.
 *
 * It is the one helper for this. Buttons built on Material's (the `RowButton`s of a
 * `StatusRow`, the button of `AttentionTile`) and MilO's own rows use it alike.
 *
 * @param drawn how high it is drawn while its words fit on one line.
 */
internal fun Modifier.takesUpOnly(drawn: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val spare =
        if (placeable.height <= FingerTarget.roundToPx()) {
            (placeable.height - drawn.roundToPx()).coerceAtLeast(0)
        } else {
            0
        }
    layout(placeable.width, placeable.height - spare) { placeable.place(0, -spare / 2) }
}
