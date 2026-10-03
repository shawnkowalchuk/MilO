package com.shawnkowalchuk.milo.platform.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reason numbers are written out here on purpose, not taken from `ApplicationExitInfo`: they
 * are Android's documented values, and the test checks the names against them independently.
 */
class ProcessExitTest {
    private fun exit(reason: Int, description: String?) = ProcessExit(
        atMs = 1_791_028_800_000,
        pid = 4321,
        reason = reason,
        description = description,
        importance = 125,
        status = 0,
    )

    @Test
    fun `a kill by a Xiaomi cleaner is named in the message`() {
        // Xiaomi's cleaners report the catch-all reason OTHER (13) and name themselves in the
        // description. That name is the whole point of reading these records.
        val message = exit(reason = 13, description = "SwipeUpClean").message()

        assertEquals("Process 4321 ended: OTHER (SwipeUpClean)", message)
    }

    @Test
    fun `an exit without a description has none in the message`() {
        assertEquals("Process 4321 ended: LOW_MEMORY", exit(3, description = null).message())
        assertEquals("Process 4321 ended: LOW_MEMORY", exit(3, description = "  ").message())
    }

    @Test
    fun `the detail keeps the raw numbers`() {
        assertEquals("reason=13 importance=125 status=0", exit(13, "SwipeUpClean").detail())
    }

    @Test
    fun `the reasons that matter for a missed trip have their documented names`() {
        assertEquals("SIGNALED", reasonName(2))
        assertEquals("LOW_MEMORY", reasonName(3))
        assertEquals("CRASH", reasonName(4))
        assertEquals("ANR", reasonName(6))
        // The user asked for it: a force stop, or a swipe from recents. The description says
        // which (on the emulator a force stop reads "[FORCE STOP] stop ...").
        assertEquals("USER_REQUESTED", reasonName(10))
        // Not a force stop, despite the name: the Android user profile itself was stopped.
        assertEquals("USER_STOPPED", reasonName(11))
        assertEquals("OTHER", reasonName(13))
        assertEquals("FREEZER", reasonName(14))
        assertEquals("PACKAGE_UPDATED", reasonName(16))
    }

    @Test
    fun `a reason this code has never heard of is shown as its number`() {
        assertEquals("reason 99", reasonName(99))
        assertEquals("Process 4321 ended: reason 99", exit(99, description = null).message())
    }
}
