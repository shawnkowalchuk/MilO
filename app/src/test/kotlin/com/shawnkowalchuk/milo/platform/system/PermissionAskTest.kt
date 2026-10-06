package com.shawnkowalchuk.milo.platform.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling a permission dialog that never appeared from one that was shown and refused. */
class PermissionAskTest {
    @Test
    fun `a granted permission never sends Shawn to the settings`() {
        for (before in listOf(false, true)) {
            for (after in listOf(false, true)) {
                assertFalse(androidDidNotAsk(granted = true, before, after))
            }
        }
    }

    @Test
    fun `the first refusal was a dialog that was shown`() {
        // Android starts to say "explain" once the dialog has been refused.
        assertFalse(
            androidDidNotAsk(granted = false, couldExplainBefore = false, canExplainAfter = true),
        )
    }

    @Test
    fun `the second refusal was a dialog that was shown`() {
        // Android stops saying "explain" once it will not ask again; it did ask this time.
        assertFalse(
            androidDidNotAsk(granted = false, couldExplainBefore = true, canExplainAfter = false),
        )
    }

    @Test
    fun `refused with nothing to explain before or after means no dialog appeared`() {
        assertTrue(
            androidDidNotAsk(granted = false, couldExplainBefore = false, canExplainAfter = false),
        )
    }
}
