package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The icons of the bottom navigation bar, the back arrow, the way to Settings and the two
// buttons of a stepper. Like the status indicators (StatusIcons.kt) they are built from path
// data here, because the material-icons library is frozen and a handful of icons does not
// justify a dependency. The outlines are Google's Material "home", "date range", "checklist",
// "list", "arrow back", "settings", "add" and "remove" icons (Apache License 2.0).

private val IconSize = 24.dp
private const val VIEWPORT_SIZE = 24f

private const val HOME_PATH = "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"

// A calendar page: the Trips screen shows one month at a time.
private const val DATE_RANGE_PATH =
    "M9,11L7,11v2h2v-2zM13,11h-2v2h2v-2zM17,11h-2v2h2v-2z" +
        "M19,4h-1L18,2h-2v2L8,4L8,2L6,2v2L5,4c-1.11,0 -1.99,0.9 -1.99,2L3,20" +
        "c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2L21,6c0,-1.1 -0.9,-2 -2,-2z" +
        "M19,20L5,20L5,9h14v11z"

// Two ticked lines: the Setup screen is a checklist.
private const val CHECKLIST_PATH =
    "M22,7h-9v2h9V7zM22,15h-9v2h9V15z" +
        "M5.54,11L2,7.46l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,11z" +
        "M5.54,19L2,15.46l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,19z"

// Bulleted lines: the event log.
private const val LIST_PATH =
    "M3,13h2v-2L3,11v2zM3,17h2v-2L3,15v2zM3,9h2L5,7L3,7v2z" +
        "M7,13h14v-2L7,11v2zM7,17h14v-2L7,15v2zM7,7v2h14L21,7L7,7z"

private const val ARROW_BACK_PATH =
    "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"

// A cog wheel: the Settings screen, opened from Home.
private const val SETTINGS_PATH =
    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
        "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22" +
        "l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 " +
        "-0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29" +
        "L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48" +
        "l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58" +
        "c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96" +
        "c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84" +
        "c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96" +
        "c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z" +
        "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6" +
        "S13.98,15.6 12,15.6z"

// Plus and minus: one step up and one step down in a StepperRow.
private const val ADD_PATH = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
private const val REMOVE_PATH = "M19,13H5v-2h14v2z"

/** The icons screens may use. Each is tinted by the component that draws it. */
object MiloIcons {
    val Home: ImageVector by lazy { pathIcon(name = "Home", pathData = HOME_PATH) }
    val Trips: ImageVector by lazy { pathIcon(name = "Trips", pathData = DATE_RANGE_PATH) }
    val Setup: ImageVector by lazy { pathIcon(name = "Setup", pathData = CHECKLIST_PATH) }
    val Log: ImageVector by lazy { pathIcon(name = "Log", pathData = LIST_PATH) }
    val Back: ImageVector by lazy { pathIcon(name = "Back", pathData = ARROW_BACK_PATH) }
    val Settings: ImageVector by lazy { pathIcon(name = "Settings", pathData = SETTINGS_PATH) }
    val Add: ImageVector by lazy { pathIcon(name = "Add", pathData = ADD_PATH) }
    val Remove: ImageVector by lazy { pathIcon(name = "Remove", pathData = REMOVE_PATH) }
}

/** Builds a 24 dp icon from the path data of a Material icon. */
internal fun pathIcon(name: String, pathData: String): ImageVector = ImageVector
    .Builder(
        name = name,
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = VIEWPORT_SIZE,
        viewportHeight = VIEWPORT_SIZE,
    ).addPath(
        pathData = addPathNodes(pathData),
        // Never seen: Icon() replaces this fill with its tint, so the visible colour always comes
        // from the theme.
        fill = SolidColor(Color.Black),
    ).build()
