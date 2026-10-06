package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The icons of the bottom navigation bar and the back arrow. Like the status indicators
// (StatusIcons.kt) they are built from path data here, because the material-icons library is
// frozen and five icons do not justify a dependency. The outlines are Google's Material "home",
// "date range", "checklist", "list" and "arrow back" icons (Apache License 2.0).

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

/** The icons screens may use. Each is tinted by the component that draws it. */
object MiloIcons {
    val Home: ImageVector by lazy { pathIcon(name = "Home", pathData = HOME_PATH) }
    val Trips: ImageVector by lazy { pathIcon(name = "Trips", pathData = DATE_RANGE_PATH) }
    val Setup: ImageVector by lazy { pathIcon(name = "Setup", pathData = CHECKLIST_PATH) }
    val Log: ImageVector by lazy { pathIcon(name = "Log", pathData = LIST_PATH) }
    val Back: ImageVector by lazy { pathIcon(name = "Back", pathData = ARROW_BACK_PATH) }
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
