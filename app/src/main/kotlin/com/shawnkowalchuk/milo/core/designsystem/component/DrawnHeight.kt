package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Android's smallest target for a finger. Material makes every control at least this high. */
private val TouchTarget = 48.dp

/**
 * Lets a control that the design draws lower than Android's 48 dp target take up only the
 * height it is drawn at, so that what it stands in is as high as drawn.
 *
 * Material keeps the difference free around such a control, half above and half below. With
 * this, that room is no longer counted: it reaches into the padding of whatever the control
 * stands in. **The place a finger can hit is not made smaller,** so only use it where there is
 * at least that much padding above and below.
 *
 * A control that has grown past the target, because its words needed a second line, is left as
 * it is.
 *
 * `AttentionTile` has a copy of its own for its 44 dp button, written before this one.
 *
 * @param drawn how high the design draws the control.
 */
// TODO(debt): the same job as takesUpOnly in PressArea.kt, written at the same time on another
// branch. Keep one of the two (FINDINGS_LOG, 2026-10-07).
internal fun Modifier.takesDrawnHeight(drawn: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val margin =
        if (placeable.height > TouchTarget.roundToPx()) {
            0
        } else {
            (placeable.height - drawn.roundToPx()).coerceAtLeast(0)
        }
    layout(placeable.width, placeable.height - margin) { placeable.place(0, -margin / 2) }
}
