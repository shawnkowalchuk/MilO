package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home-screen widget's colours are written in XML, because the home screen app draws the
 * widget outside Compose. Each is a colour of the theme, so that the widget looks like the
 * app's own tiles. Nothing but this test would notice the two drifting apart.
 */
class WidgetColorsTest {
    @Test
    fun `the widget is coloured in the colours of the theme`() {
        // Gradle runs the unit tests from the module's folder, app/.
        val file = File("src/main/res/values/colors.xml")
        assertTrue("Not found: ${file.absolutePath}", file.isFile)
        val xml = file.readText()
        val scheme = MiloColorScheme
        val roles =
            mapOf(
                "milo_widget_tile" to scheme.surfaceContainer,
                "milo_widget_text" to scheme.onSurface,
                "milo_widget_text_secondary" to scheme.onSurfaceVariant,
                "milo_widget_accent" to scheme.primary,
                "milo_widget_on_accent" to scheme.onPrimary,
                "milo_widget_control" to scheme.secondaryContainer,
            )

        for ((name, role) in roles) {
            val written =
                Regex("""<color name="$name">#([0-9A-Fa-f]{6})</color>""")
                    .find(xml)
                    ?.groupValues
                    ?.get(1)
            assertEquals(name, hex(role), written?.uppercase())
        }
    }

    private fun hex(color: Color): String = "%06X".format(color.toArgb() and 0xFFFFFF)
}
