package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notifications' colour is written in XML, because Android reads it outside Compose. It is
 * the theme's accent, the lime of the app's mark, so that the notification icon in the shade
 * looks like the mark. Nothing but this test would notice the two drifting apart.
 */
class NotificationAccentTest {
    @Test
    fun `the notifications are coloured in the accent of the theme`() {
        // Gradle runs the unit tests from the module's folder, app/.
        val file = File("src/main/res/values/colors.xml")
        assertTrue("Not found: ${file.absolutePath}", file.isFile)

        val written =
            Regex("""<color name="milo_notification_accent">#([0-9A-Fa-f]{6})</color>""")
                .find(file.readText())
                ?.groupValues
                ?.get(1)
        val accent = "%06X".format(MiloColorScheme.primary.toArgb() and 0xFFFFFF)

        assertEquals(accent, written?.uppercase())
    }
}
