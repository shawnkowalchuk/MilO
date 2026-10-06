package com.shawnkowalchuk.milo.platform.car

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one event-log line of the car session that carries what nobody can look up: which host
 * opened MilO, and which Car API level it agreed on. The session itself needs a car.
 */
class MiloCarSessionTest {
    @Test
    fun `the line names the host and the level`() {
        assertEquals(
            "Android Auto screen: session created by com.google.android.projection.gearhead, " +
                "Car API level 7",
            sessionCreatedText("com.google.android.projection.gearhead", 7),
        )
    }

    @Test
    fun `a host or a level that could not be read is said, not left out`() {
        assertEquals(
            "Android Auto screen: session created by a host that gave no name, " +
                "Car API level not agreed",
            sessionCreatedText(hostPackage = null, carApiLevel = null),
        )
    }
}
