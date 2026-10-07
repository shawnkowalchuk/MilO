package com.shawnkowalchuk.milo.feature.setup

import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.system.SetupDetail
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SetupItem
import com.shawnkowalchuk.milo.platform.system.SetupRow
import com.shawnkowalchuk.milo.platform.system.SetupState
import com.shawnkowalchuk.milo.platform.system.SystemScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The count in the tile at the top of Setup: every row in exactly one of four states. And the
 * order the rows are listed in.
 */
class SetupSummaryTest {
    private fun row(item: SetupItem, state: SetupState, detail: SetupDetail = SetupDetail.FINE) =
        SetupRow(item, state, detail)

    /** The owner's drawing: fourteen rows, two problems and one to confirm. */
    private val drawn: List<SetupRow> =
        SetupItem.entries.map { item ->
            when (item) {
                SetupItem.TRUCK ->
                    SetupRow(
                        item,
                        SetupState.PROBLEM,
                        SetupDetail.TRUCK_NOT_PAIRED,
                        SetupFix.OpenPairing,
                    )

                SetupItem.PHYSICAL_ACTIVITY -> row(item, SetupState.PROBLEM, SetupDetail.NOT_SET)

                SetupItem.XIAOMI_RECENTS_LOCK ->
                    SetupRow(
                        item,
                        SetupState.NEEDS_CONFIRMATION,
                        SetupDetail.NOT_CONFIRMED,
                        confirmStep = ConfirmedStep.XIAOMI_RECENTS_LOCK,
                    )

                else -> row(item, SetupState.OK)
            }
        }

    @Test
    fun `the drawing's checklist is counted as the drawing says`() {
        val summary = setupSummary(drawn)

        assertEquals(
            SetupSummary(total = 14, ready = 11, toFix = 2, toConfirm = 1, notChecked = 0),
            summary,
        )
        assertFalse(summary.allReady)
        // The drawing fills its bar to 79 percent.
        assertEquals(0.79f, summary.readyShare, 0.005f)
    }

    @Test
    fun `a row MilO could not read is counted by itself, and as nothing else`() {
        val rows =
            listOf(
                row(SetupItem.PRECISE_LOCATION, SetupState.OK),
                // The pairing check failed: nothing says the truck is wrong, or right.
                row(SetupItem.TRUCK, SetupState.UNKNOWN, SetupDetail.TRUCK_CHECK_FAILED),
                SetupRow(
                    SetupItem.XIAOMI_AUTOSTART,
                    SetupState.UNKNOWN,
                    SetupDetail.AUTOSTART_UNREADABLE,
                    SetupFix.Open(SystemScreen.XIAOMI_AUTOSTART),
                    ConfirmedStep.XIAOMI_AUTOSTART,
                ),
            )

        assertEquals(
            SetupSummary(total = 3, ready = 1, toFix = 0, toConfirm = 0, notChecked = 2),
            setupSummary(rows),
        )
    }

    @Test
    fun `a recommended row that is not in order counts, though Home does not warn for it`() {
        // Home's warning goes by the required rows. The count is of every row, as drawn.
        val rows =
            listOf(
                row(SetupItem.PRECISE_LOCATION, SetupState.OK),
                row(SetupItem.UNUSED_APP_PAUSE, SetupState.PROBLEM, SetupDetail.NOT_SET),
            )
        assertFalse(SetupItem.UNUSED_APP_PAUSE.required)

        val summary = setupSummary(rows)

        assertEquals(1, summary.toFix)
        assertFalse(summary.allReady)
    }

    @Test
    fun `a setting Shawn confirmed is ready`() {
        val confirmed =
            SetupRow(
                SetupItem.XIAOMI_RECENTS_LOCK,
                SetupState.OK,
                SetupDetail.CONFIRMED,
                confirmStep = ConfirmedStep.XIAOMI_RECENTS_LOCK,
                confirmedAtMs = 1_791_028_800_000,
            )

        assertEquals(1, setupSummary(listOf(confirmed)).ready)
    }

    @Test
    fun `with every row in order there is nothing left, and the bar is full`() {
        val summary = setupSummary(SetupItem.entries.map { row(it, SetupState.OK) })

        assertTrue(summary.allReady)
        assertEquals(1f, summary.readyShare, 0f)
        assertEquals(0, summary.toFix + summary.toConfirm + summary.notChecked)
    }

    @Test
    fun `the four counts always add up to the number of rows`() {
        // Every state on every row: no row may fall between the counts, or into two of them.
        for (state in SetupState.entries) {
            val rows = SetupItem.entries.map { row(it, state) } + drawn
            val summary = setupSummary(rows)

            assertEquals(
                summary.total,
                summary.ready + summary.toFix + summary.toConfirm + summary.notChecked,
            )
            assertEquals(rows.size, summary.total)
        }
    }

    @Test
    fun `no rows at all is not a full bar`() {
        assertEquals(0f, setupSummary(emptyList()).readyShare, 0f)
    }

    // ---- The order of the rows ------------------------------------------------------------------

    @Test
    fun `the rows to fix stand first, as on the drawing, and the rest keep their order`() {
        val android = drawn.filterNot { it.item.xiaomiOnly }

        assertEquals(
            // The drawing's own order: the truck and Physical activity, then the eight ticks.
            listOf(
                SetupItem.TRUCK,
                SetupItem.PHYSICAL_ACTIVITY,
                SetupItem.PRECISE_LOCATION,
                SetupItem.BACKGROUND_LOCATION,
                SetupItem.NOTIFICATIONS,
                SetupItem.NEARBY_DEVICES,
                SetupItem.LOCATION_SERVICES,
                SetupItem.BATTERY_EXEMPTION,
                SetupItem.UNUSED_APP_PAUSE,
                SetupItem.BATTERY_SAVER_OFF,
            ),
            toFixFirst(android).map { it.item },
        )
    }

    @Test
    fun `a row that waits for Shawn's word or could not be read stays in its place`() {
        val hyperOs = drawn.filter { it.item.xiaomiOnly }
        // As drawn: the row to confirm is the last of its group, not the first.
        assertEquals(hyperOs, toFixFirst(hyperOs))

        val unread =
            listOf(
                row(SetupItem.PRECISE_LOCATION, SetupState.OK),
                row(SetupItem.TRUCK, SetupState.UNKNOWN, SetupDetail.TRUCK_CHECK_FAILED),
                row(SetupItem.BATTERY_EXEMPTION, SetupState.OK),
            )
        assertEquals(unread, toFixFirst(unread))
    }

    @Test
    fun `the row a sentence calls the row above is still above`() {
        // "Allow precise location first, in the row above."
        val rows =
            listOf(
                row(SetupItem.NOTIFICATIONS, SetupState.OK),
                row(SetupItem.PRECISE_LOCATION, SetupState.PROBLEM, SetupDetail.NOT_SET),
                row(
                    SetupItem.BACKGROUND_LOCATION,
                    SetupState.PROBLEM,
                    SetupDetail.PRECISE_LOCATION_FIRST,
                ),
            )

        assertEquals(
            listOf(
                SetupItem.PRECISE_LOCATION,
                SetupItem.BACKGROUND_LOCATION,
                SetupItem.NOTIFICATIONS,
            ),
            toFixFirst(rows).map { it.item },
        )
    }

    @Test
    fun `putting the rows in order loses none and adds none`() {
        for (state in SetupState.entries) {
            val rows = SetupItem.entries.map { row(it, state) } + drawn

            assertEquals(rows.size, toFixFirst(rows).size)
            assertEquals(rows.toSet(), toFixFirst(rows).toSet())
        }
        assertTrue(toFixFirst(emptyList()).isEmpty())
    }
}
