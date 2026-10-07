package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * The design's corner radii, each named after what it rounds. Components take a shape by that
 * name (`MiloTheme.shapes.tile`), never a radius, so a corner is decided here once.
 *
 * Two of them, [wideButton] and [segment], round parts that come with the other screens' own
 * layouts; no component uses them yet.
 *
 * @param tile a tile: the rounded panel every screen is built from. Also a dialog.
 * @param smallTile the bottom bar, and in the design a tile that shares a line with another.
 * @param mainButton the one main button of a screen.
 * @param wideButton a secondary button as wide as the screen, and the frame around a row of
 * segments.
 * @param control a small button, a text field, a square icon button.
 * @param segment one segment of a segmented choice.
 * @param appMark the square with the app's initial at the top of Home.
 * @param pill fully round ends: a chip, a dot, a switch, and in the design a pill.
 */
@Immutable
data class MiloShapes(
    val tile: CornerBasedShape = RoundedCornerShape(26.dp),
    val smallTile: CornerBasedShape = RoundedCornerShape(22.dp),
    val mainButton: CornerBasedShape = RoundedCornerShape(18.dp),
    val wideButton: CornerBasedShape = RoundedCornerShape(16.dp),
    val control: CornerBasedShape = RoundedCornerShape(14.dp),
    val segment: CornerBasedShape = RoundedCornerShape(12.dp),
    val appMark: CornerBasedShape = RoundedCornerShape(11.dp),
    val pill: CornerBasedShape = CircleShape,
)

/**
 * The same radii in Material's five sizes, for the stock Material components that pick a shape
 * by size: its menus are the smallest, its chips and the boxes of the time picker `small`, its
 * cards `medium`, its dialogs and sheets `extraLarge`. A dialog is a tile in this design, which
 * is why the two largest sizes are the tile's.
 *
 * Material's buttons are not in this list: Material rounds them fully, whatever the theme says.
 *
 * **Three stock components do not land on the design through the theme, and none is used
 * today.** Do not reach for them:
 * - Material's `Card` is not a tile: it takes the colour of a control and the smaller corner. A
 *   tile is `SectionCard`, or a `Surface` in `surfaceContainer` with `MiloTheme.shapes.tile`.
 * - A bare `AlertDialog` sets its title too large and its text small and grey. A question is
 *   asked with `ConfirmDialog`, which sets both.
 * - `OutlinedButton` draws its outline in the divider's colour, which cannot be seen on a tile.
 *   The design has no outlined button.
 */
internal val MiloMaterialShapes: Shapes =
    MiloShapes().run {
        Shapes(
            extraSmall = segment,
            small = control,
            medium = smallTile,
            large = tile,
            extraLarge = tile,
        )
    }
