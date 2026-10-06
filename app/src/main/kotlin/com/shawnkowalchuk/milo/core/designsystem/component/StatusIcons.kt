package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The two indicator glyphs are built here rather than taken from the material-icons library:
// that library is frozen at 1.7.8 even inside the Compose BOM, and two icons do not justify a
// dependency. The outlines are Google's Material "check circle" and "error" icons
// (Apache License 2.0).
//
// The shapes differ as well as the colours on purpose, so the state is still readable for
// someone who cannot tell red from green, or in sunlight that washes the colours out.

private val IconSize = 24.dp
private const val VIEWPORT_SIZE = 24f

private const val CHECK_CIRCLE_PATH =
    "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
        "M10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"

private const val ERROR_CIRCLE_PATH =
    "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
        "M13,17h-2v-2h2v2zM13,13h-2L11,7h2v6z"

internal object StatusIcons {
    val Ok: ImageVector by lazy { statusIcon(name = "StatusOk", pathData = CHECK_CIRCLE_PATH) }
    val Problem: ImageVector by lazy {
        statusIcon(name = "StatusProblem", pathData = ERROR_CIRCLE_PATH)
    }
}

private fun statusIcon(name: String, pathData: String): ImageVector = ImageVector
    .Builder(
        name = name,
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = VIEWPORT_SIZE,
        viewportHeight = VIEWPORT_SIZE,
    ).addPath(
        pathData = addPathNodes(pathData),
        // Never seen: Icon() replaces this fill with its tint, so the visible colour always comes
        // from the theme's status colours.
        fill = SolidColor(Color.Black),
    ).build()
