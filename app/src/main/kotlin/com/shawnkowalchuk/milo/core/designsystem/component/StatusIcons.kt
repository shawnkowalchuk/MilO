package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector

// The marks drawn on the status dots of a StatusRow: a tick, an exclamation mark and a question
// mark. They are line icons built from path data, like the icons in MiloIcons.kt, and for the
// same reason. The fourth state has no mark: it is an empty ring, which the row draws itself.
//
// The marks differ as well as the colours on purpose, so the state is still readable for
// someone who cannot tell red from green, or in sunlight that washes the colours out.
//
// They are drawn as shapes and not written as letters, so that they keep their size on the dot
// when the phone's font size is turned up.

/** A mark is drawn small, 14 dp on a 28 dp dot, so its lines are thick on the 24 grid. */
private const val MARK_LINE_WIDTH = 3f

/**
 * The exclamation mark and the question mark are narrow, and at the tick's thickness they
 * looked thin beside it on the dot (seen on an emulator), so their lines are a little thicker.
 */
private const val NARROW_MARK_LINE_WIDTH = 3.5f

// The tick of the design.
private const val TICK_PATH = "M5,12l5,5L20,7"

// A stroke with a dot under it, as tall as the grid allows.
private const val EXCLAMATION_PATH = "M12,2.5v12M12,21.25h0.01"

// A hook with a dot under it: MilO cannot tell.
private const val QUESTION_PATH =
    "M6.76,7a5.4,5.4 0 0 1 10.5,1.8c0,3.6 -5.4,5.4 -5.4,5.4" +
        "M12,21.25h0.01"

internal object StatusIcons {
    val Tick: ImageVector by lazy {
        lineIcon(name = "StatusTick", pathData = TICK_PATH, lineWidth = MARK_LINE_WIDTH)
    }
    val Exclamation: ImageVector by lazy {
        lineIcon(
            name = "StatusExclamation",
            pathData = EXCLAMATION_PATH,
            lineWidth = NARROW_MARK_LINE_WIDTH,
        )
    }
    val Question: ImageVector by lazy {
        lineIcon(
            name = "StatusQuestion",
            pathData = QUESTION_PATH,
            lineWidth = NARROW_MARK_LINE_WIDTH,
        )
    }
}
