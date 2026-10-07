package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import com.shawnkowalchuk.milo.core.report.ReportInk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The report for the accountant is drawn in the app's colours, but outside Compose, so it
 * repeats four of them (`ReportInk` in `core/report/`). Nothing but this test would notice the
 * two drifting apart.
 */
class ReportInkTest {
    @Test
    fun `the report's dark, accent and words on dark are the theme's own`() {
        val theme = MiloColorScheme

        assertEquals(hex(theme.background.toArgb()), hex(ReportInk.DARK.argb))
        assertEquals(hex(theme.primary.toArgb()), hex(ReportInk.ACCENT.argb))
        assertEquals(hex(theme.onSurface.toArgb()), hex(ReportInk.ON_DARK.argb))
        assertEquals(hex(theme.onSurfaceVariant.toArgb()), hex(ReportInk.ON_DARK_QUIET.argb))
    }

    /** "FF121316": a colour a failure message can be read by. */
    private fun hex(argb: Int): String = "%08X".format(argb)
}
