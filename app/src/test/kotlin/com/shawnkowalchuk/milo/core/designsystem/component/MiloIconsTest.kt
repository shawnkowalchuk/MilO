package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The icons are written out as path data, by hand. A slip in one of those strings would only
 * show when the icon is first drawn, as a crash, so each one is built here once.
 */
class MiloIconsTest {
    private val icons =
        listOf(
            MiloIcons.Home,
            MiloIcons.Trips,
            MiloIcons.Setup,
            MiloIcons.Log,
            MiloIcons.Back,
            MiloIcons.Settings,
            MiloIcons.Share,
            MiloIcons.Add,
            MiloIcons.Remove,
            MiloIcons.Play,
            MiloIcons.Stop,
            MiloIcons.Phone,
            MiloIcons.Truck,
            MiloIcons.Bluetooth,
            MiloIcons.Link,
            StatusIcons.Tick,
            StatusIcons.Exclamation,
            StatusIcons.Question,
        )

    @Test
    fun `every icon's path data can be read`() {
        for (icon in icons) {
            assertTrue(icon.name, icon.root.size > 0)
        }
    }

    @Test
    fun `every icon has a name of its own`() {
        assertEquals(icons.size, icons.map { it.name }.toSet().size)
    }
}
