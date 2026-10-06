package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector

// The indicator glyphs are built here rather than taken from the material-icons library: that
// library is frozen at 1.7.8 even inside the Compose BOM, and a handful of icons does not
// justify a dependency. The outlines are Google's Material "check circle", "error", "help" and
// "radio button unchecked" icons (Apache License 2.0).
//
// The shapes differ as well as the colours on purpose, so the state is still readable for
// someone who cannot tell red from green, or in sunlight that washes the colours out.

/** The circle all four indicators are drawn in. */
private const val CIRCLE = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z"

private const val CHECK_CIRCLE_PATH =
    CIRCLE + "M10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"

private const val ERROR_CIRCLE_PATH = CIRCLE + "M13,17h-2v-2h2v2zM13,13h-2L11,7h2v6z"

// A question mark: MilO cannot tell.
private const val HELP_CIRCLE_PATH =
    CIRCLE + "M13,19h-2v-2h2v2z" +
        "M15.07,11.25l-0.9,0.92C13.45,12.9 13,13.5 13,15h-2v-0.5" +
        "c0,-1.1 0.45,-2.1 1.17,-2.83l1.24,-1.26c0.37,-0.36 0.59,-0.86 0.59,-1.41 " +
        "0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2L8,9c0,-2.21 1.79,-4 4,-4s4,1.79 4,4" +
        "c0,0.88 -0.36,1.68 -0.93,2.25z"

// An empty ring, like a box waiting for its tick: the user has something to confirm.
private const val EMPTY_CIRCLE_PATH =
    CIRCLE + "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z"

internal object StatusIcons {
    val Ok: ImageVector by lazy { pathIcon(name = "StatusOk", pathData = CHECK_CIRCLE_PATH) }
    val Problem: ImageVector by lazy {
        pathIcon(name = "StatusProblem", pathData = ERROR_CIRCLE_PATH)
    }
    val Unknown: ImageVector by lazy {
        pathIcon(name = "StatusUnknown", pathData = HELP_CIRCLE_PATH)
    }
    val NeedsConfirmation: ImageVector by lazy {
        pathIcon(name = "StatusNeedsConfirmation", pathData = EMPTY_CIRCLE_PATH)
    }
}
