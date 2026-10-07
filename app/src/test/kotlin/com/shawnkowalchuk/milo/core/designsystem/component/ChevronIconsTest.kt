package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two arrowheads are written out as path data, by hand, like the icons of `MiloIcons`. A
 * slip in one of those strings would only show when the icon is first drawn, as a crash, so
 * each one is built here once.
 */
class ChevronIconsTest {
    private val icons = listOf(ChevronIcons.Forward, ChevronIcons.Down)

    @Test
    fun `both arrowheads' path data can be read`() {
        for (icon in icons) {
            assertTrue(icon.name, icon.root.size > 0)
        }
    }

    @Test
    fun `neither takes the name of another icon`() {
        val others = listOf(MiloIcons.Back, MiloIcons.Add, MiloIcons.Remove).map { it.name }

        assertEquals(icons.size, icons.map { it.name }.toSet().size)
        assertTrue(icons.none { it.name in others })
    }
}
