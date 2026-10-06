package com.shawnkowalchuk.milo.platform.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two decisions made about what the phone reports before the checklist's rules see it. */
class SetupFactsTest {
    @Test
    fun `a Xiaomi, Redmi or POCO phone is recognised by manufacturer or brand, in any case`() {
        // What the POCO X5 Pro 5G reports.
        assertTrue(isXiaomiFamily(manufacturer = "Xiaomi", brand = "POCO"))
        assertTrue(isXiaomiFamily(manufacturer = "xiaomi", brand = "Redmi"))
        assertTrue(isXiaomiFamily(manufacturer = "XIAOMI", brand = null))
        assertTrue(isXiaomiFamily(manufacturer = null, brand = "poco"))
    }

    @Test
    fun `other phones get no HyperOS rows`() {
        assertFalse(isXiaomiFamily(manufacturer = "Google", brand = "google"))
        assertFalse(isXiaomiFamily(manufacturer = "samsung", brand = "samsung"))
        assertFalse(isXiaomiFamily(manufacturer = null, brand = null))
        // A name that merely contains one of the brands is not one of them.
        assertFalse(isXiaomiFamily(manufacturer = "Pocophone Repairs", brand = "generic"))
    }

    @Test
    fun `only mode 0 of the Autostart app-op reads as on`() {
        assertEquals(AutostartReading.LOOKS_ON, autostartReadingOf(0))
        // MIUI writes 1 or 2 for "off", depending on the version.
        assertEquals(AutostartReading.LOOKS_OFF, autostartReadingOf(1))
        assertEquals(AutostartReading.LOOKS_OFF, autostartReadingOf(2))
        assertEquals(AutostartReading.LOOKS_OFF, autostartReadingOf(3))
    }

    @Test
    fun `a check that could not be made is unknown, never on`() {
        assertEquals(AutostartReading.UNKNOWN, autostartReadingOf(null))
    }
}
