package com.shawnkowalchuk.milo.core.designsystem.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a status row's button stands at the end of the row, as the design draws the checklist,
 * and when the buttons stay under the text. The widths are in pixels; only their proportion
 * counts.
 */
class StatusRowLayoutTest {
    @Test
    fun `one button beside a short text stands at the end of the row, as drawn`() {
        // The drawing's own narrowest case: "Locked in recent apps" beside "I have set this".
        assertTrue(buttonStandsBeside(1, textWidthAlone = 282, textWidthBeside = 159, 1))
    }

    @Test
    fun `two buttons always stand under the text`() {
        assertFalse(buttonStandsBeside(2, textWidthAlone = 282, textWidthBeside = 200, 1))
    }

    @Test
    fun `a row without a button has none to place`() {
        assertFalse(buttonStandsBeside(0, textWidthAlone = 282, textWidthBeside = 282, 1))
    }

    @Test
    fun `the text keeps at least half of its width, or the button goes under it`() {
        assertTrue(buttonStandsBeside(1, textWidthAlone = 300, textWidthBeside = 150, 1))
        // A large font or a long word on the button: one pixel under half is too little.
        assertFalse(buttonStandsBeside(1, textWidthAlone = 300, textWidthBeside = 149, 1))
    }

    @Test
    fun `a button wider than the row leaves no text at all`() {
        assertFalse(buttonStandsBeside(1, textWidthAlone = 300, textWidthBeside = 0, 0))
        assertFalse(buttonStandsBeside(1, textWidthAlone = 300, textWidthBeside = -40, 0))
    }

    @Test
    fun `an instruction of more than three lines gets the whole width`() {
        val limit = MAX_NOTE_LINES_BESIDE_A_BUTTON

        assertTrue(buttonStandsBeside(1, textWidthAlone = 282, textWidthBeside = 200, limit))
        assertFalse(buttonStandsBeside(1, textWidthAlone = 282, textWidthBeside = 200, limit + 1))
    }

    @Test
    fun `a row with a name and no second line can have its button beside it`() {
        assertTrue(buttonStandsBeside(1, textWidthAlone = 282, textWidthBeside = 200, 0))
    }
}
