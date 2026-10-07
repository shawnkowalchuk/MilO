package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How large the design draws the square. */
private val SquareSize = 44.dp

/** The icon in it. The design's are 18 to 20; one size keeps every square alike. */
private val IconSize = 20.dp

/**
 * The design's square button with one icon in it: the back arrow and the action beside a
 * screen's title, and the minus and the plus of a stepper.
 *
 * It is drawn 44 dp square and takes up 48: Material's clickable `Surface` adds the difference
 * around it, so the place a finger has to hit is never smaller than Android asks for.
 *
 * @param description what a screen reader says for the icon, and so what the button does.
 * @param fill the square's colour. A square on the page is a tile; one that sits on a tile has
 * to be passed the colour of a control, or it would not be seen.
 * @param enabled false greys the icon and takes the press away; the square stays, so that
 * nothing around it moves. The grey is that of secondary text, as on a main button that is
 * switched off, so the icon can still be made out: a button that has to wait is greyed, never
 * hidden. The design's own grey for an idle icon is too faint for that on a control's fill.
 * @param greyedIcon the icon's colour while [enabled] is false. A square that stands on the
 * page is passed the design's own idle grey, which can be made out there.
 */
@Composable
internal fun SquareIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainer,
    enabled: Boolean = true,
    greyedIcon: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button },
        enabled = enabled,
        shape = MiloTheme.shapes.control,
        color = fill,
        contentColor =
            if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                greyedIcon
            },
    ) {
        Box(modifier = Modifier.size(SquareSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}
