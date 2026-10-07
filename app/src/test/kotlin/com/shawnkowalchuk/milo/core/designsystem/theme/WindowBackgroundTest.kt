package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's background is the one colour of the screens that is written in XML, because
 * Android paints it before Compose exists. It has to be the theme's page colour, or a launch
 * shows a flash of some other colour before the first screen. Nothing but this test would
 * notice the two drifting apart.
 */
class WindowBackgroundTest {
    @Test
    fun `the window is painted in the page colour of the theme`() {
        // Gradle runs the unit tests from the module's folder, app/.
        val file = File("src/main/res/values/colors.xml")
        assertTrue("Not found: ${file.absolutePath}", file.isFile)

        val written =
            Regex("""<color name="milo_window_background">#([0-9A-Fa-f]{6})</color>""")
                .find(file.readText())
                ?.groupValues
                ?.get(1)
        val page = "%06X".format(MiloColorScheme.background.toArgb() and 0xFFFFFF)

        assertEquals(page, written?.uppercase())
    }
}
