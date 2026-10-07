package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paper plane is written out as path data, by hand, like the icons of `MiloIcons`. A slip
 * in that string would only show when the icon is first drawn, as a crash, so it is built here
 * once.
 */
class ReportIconsTest {
    @Test
    fun `the paper plane's path data can be read`() {
        assertTrue(ReportIcons.Send.root.size > 0)
    }

    @Test
    fun `it does not take the name of another icon`() {
        val others =
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
                ChevronIcons.Forward,
                ChevronIcons.Down,
            ).map { it.name }

        assertEquals(others.size, others.toSet().size)
        assertTrue(ReportIcons.Send.name !in others)
    }
}
