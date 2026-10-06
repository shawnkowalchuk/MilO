package com.shawnkowalchuk.milo.core.designsystem.component

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a screen counts as back in front of Shawn because MilO's window got the focus back: the
 * quick settings panel closing. The effect itself, and whether HyperOS's panel takes the focus
 * at all, can only be tested on the phone.
 */
class CameToFrontEffectTest {
    @Test
    fun `a screen that is entered with the focus has not come back`() = runTest {
        assertEquals(0, flowOf(true).focusRegained().count())
    }

    @Test
    fun `opening the quick settings is not a return, and closing them is`() = runTest {
        assertEquals(0, flowOf(true, false).focusRegained().count())
        assertEquals(1, flowOf(true, false, true).focusRegained().count())
    }

    @Test
    fun `every time the panel closes counts again`() = runTest {
        assertEquals(2, flowOf(true, false, true, false, true).focusRegained().count())
    }

    @Test
    fun `a screen entered without the focus comes to the front when it gets it`() = runTest {
        // MilO's first screen is drawn a moment before Android gives its window the focus.
        assertEquals(1, flowOf(false, true).focusRegained().count())
    }
}
