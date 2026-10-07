package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The icons of the bottom bar, the back arrow, the way to Settings and the two buttons of a
// stepper. Like the marks of the status dots (StatusIcons.kt) they are built from path data
// here, because the material-icons library is frozen and a handful of icons does not justify a
// dependency.
//
// They are the line icons of the owner's "Bento" design: drawn as lines on a 24 by 24 grid, with
// round ends and round corners, not as filled shapes. The outlines are taken from the design
// canvas as they are drawn there. Several of them match, or come close to, icons of the Feather
// and Lucide sets, which are published under the MIT and ISC licences.

private val IconSize = 24.dp
private const val VIEWPORT_SIZE = 24f

/** How thick the design draws the lines of an icon, on the 24 grid. */
private const val LINE_WIDTH = 1.8f

/** The design draws a chevron, a plus and a minus a little thicker: they are simpler shapes. */
private const val BOLD_LINE_WIDTH = 2f

private const val HOME_PATH = "M3,10.5L12,3l9,7.5V21h-6v-6H9v6H3z"

// Three lines, each with a dot before it: the Trips screen is a list of trips.
private const val TRIPS_PATH =
    "M8,6h13M8,12h13M8,18h13" +
        "M3,6h0.01M3,12h0.01M3,18h0.01"

// A box with a tick in it: the Setup screen is a checklist.
private const val SETUP_PATH =
    "M9,11l3,3l8,-8" +
        "M20,12v7a2,2 0 0 1 -2,2H6a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2h9"

// A sheet of paper with lines on it: the event log.
private const val LOG_PATH =
    "M14,3H6a2,2 0 0 0 -2,2v14a2,2 0 0 0 2,2h12a2,2 0 0 0 2,-2V9z" +
        "M14,3v6h6M8,13h8M8,17h5"

private const val BACK_PATH = "M15,18l-6,-6l6,-6"

// Three sliders: the Settings screen, opened from Home. Each line is broken by its knob.
private const val SETTINGS_PATH =
    "M4,6h10M18,6h2M4,12h4M12,12h8M4,18h12" +
        "M14,6a2,2 0 1 0 4,0a2,2 0 1 0 -4,0" +
        "M8,12a2,2 0 1 0 4,0a2,2 0 1 0 -4,0" +
        "M16,18a2,2 0 1 0 4,0a2,2 0 1 0 -4,0"

// Plus and minus: one step up and one step down in a StepperRow.
private const val ADD_PATH = "M12,5v14M5,12h14"
private const val REMOVE_PATH = "M5,12h14"

/** The icons screens may use. Each is tinted by the component that draws it. */
object MiloIcons {
    val Home: ImageVector by lazy { lineIcon(name = "Home", pathData = HOME_PATH) }
    val Trips: ImageVector by lazy { lineIcon(name = "Trips", pathData = TRIPS_PATH) }
    val Setup: ImageVector by lazy { lineIcon(name = "Setup", pathData = SETUP_PATH) }
    val Log: ImageVector by lazy { lineIcon(name = "Log", pathData = LOG_PATH) }
    val Back: ImageVector by lazy {
        lineIcon(name = "Back", pathData = BACK_PATH, lineWidth = BOLD_LINE_WIDTH)
    }
    val Settings: ImageVector by lazy { lineIcon(name = "Settings", pathData = SETTINGS_PATH) }
    val Add: ImageVector by lazy {
        lineIcon(name = "Add", pathData = ADD_PATH, lineWidth = BOLD_LINE_WIDTH)
    }
    val Remove: ImageVector by lazy {
        lineIcon(name = "Remove", pathData = REMOVE_PATH, lineWidth = BOLD_LINE_WIDTH)
    }
}

/**
 * Builds an icon from lines drawn on a 24 by 24 grid, with round ends and round corners.
 *
 * @param lineWidth how thick the lines are, on that grid.
 */
internal fun lineIcon(name: String, pathData: String, lineWidth: Float = LINE_WIDTH): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = IconSize,
            defaultHeight = IconSize,
            viewportWidth = VIEWPORT_SIZE,
            viewportHeight = VIEWPORT_SIZE,
        ).addPath(
            pathData = addPathNodes(pathData),
            // Never seen: Icon() replaces this colour with its tint, so the visible colour always
            // comes from the theme.
            stroke = SolidColor(Color.Black),
            strokeLineWidth = lineWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
