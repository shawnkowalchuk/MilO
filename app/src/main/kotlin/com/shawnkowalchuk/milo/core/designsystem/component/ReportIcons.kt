package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector

// The icon that came with the Report screen's layout: the paper plane on "Email the report".
// It is in a file of its own, like the two arrowheads of `ChevronIcons`, because other screens
// were being laid out at the same time; it is built by the same `lineIcon`, from the outline
// the design canvas draws.

/** As thick as the design draws it, on the 24 grid. */
private const val SEND_LINE_WIDTH = 2f

// A paper plane that points up and to the right, with the fold from its nose to its middle.
private const val SEND_PATH = "M22,2l-7,20l-4,-9l-9,-4zM22,2L11,13"

/** The icons of the Report screen. Each is tinted by the part that draws it. */
object ReportIcons {
    val Send: ImageVector by lazy {
        lineIcon(name = "Send", pathData = SEND_PATH, lineWidth = SEND_LINE_WIDTH)
    }
}
