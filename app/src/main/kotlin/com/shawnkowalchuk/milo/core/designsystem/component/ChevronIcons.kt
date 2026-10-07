package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector

// The two arrowheads the design system's own parts need besides the back arrow of `MiloIcons`:
// one that points on, and one that points down. They are in a file of their own because they
// came with the Trips screen's layout, while other screens were being laid out at the same
// time; they are built by the same `lineIcon` and drawn as thick as the back arrow.

/** As thick as the design draws a chevron, on the 24 grid. */
private const val CHEVRON_LINE_WIDTH = 2f

// The back arrow's mirror image, as the design draws "next".
private const val FORWARD_PATH = "M9,18l6,-6l-6,-6"

// Points down: what is under a heading can be opened. Turned half round, it points up.
private const val DOWN_PATH = "M6,9l6,6l6,-6"

/** Arrowheads for the parts of the design system. Each is tinted by the part that draws it. */
internal object ChevronIcons {
    val Forward: ImageVector by lazy {
        lineIcon(name = "Forward", pathData = FORWARD_PATH, lineWidth = CHEVRON_LINE_WIDTH)
    }
    val Down: ImageVector by lazy {
        lineIcon(name = "Down", pathData = DOWN_PATH, lineWidth = CHEVRON_LINE_WIDTH)
    }
}
