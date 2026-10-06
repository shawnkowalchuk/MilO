package com.shawnkowalchuk.milo.feature.setup

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.platform.system.SetupDetail
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SetupItem
import com.shawnkowalchuk.milo.platform.system.SetupRow
import com.shawnkowalchuk.milo.platform.system.SetupState
import com.shawnkowalchuk.milo.platform.system.SystemScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which words a row of the checklist shows, where a wrong choice would send Shawn looking for
 * something the phone does not have. The words themselves are in `strings.xml`.
 */
class SetupTextsTest {
    private fun truckRow(detail: SetupDetail) =
        SetupRow(SetupItem.TRUCK, SetupState.PROBLEM, detail, SetupFix.OpenPairing)

    @Test
    fun `the truck row's button says Pair truck while there is no pairing to change`() {
        val nothingToChange =
            setOf(SetupDetail.TRUCK_NOT_PAIRED, SetupDetail.TRUCK_ASSOCIATION_MISSING)
        val truckDetails =
            nothingToChange +
                setOf(
                    SetupDetail.FINE,
                    SetupDetail.TRUCK_NOT_WATCHED,
                    SetupDetail.TRUCK_CHECK_FAILED,
                    SetupDetail.TRUCK_NOT_CHECKED_YET,
                )

        for (detail in truckDetails) {
            // "Pair it again" beside a button that said "Change truck" was the defect.
            val expected =
                if (detail in nothingToChange) {
                    R.string.setup_action_pair_truck
                } else {
                    R.string.setup_action_change_truck
                }

            assertEquals("$detail", expected, truckRow(detail).fixLabelRes())
        }
    }

    @Test
    fun `the Physical activity row says why it is not asked for while the alert is off`() {
        fun physicalActivity(state: SetupState, detail: SetupDetail) =
            SetupRow(SetupItem.PHYSICAL_ACTIVITY, state, detail)

        assertEquals(
            R.string.setup_detail_driving_alert_off,
            physicalActivity(SetupState.OK, SetupDetail.DRIVING_ALERT_OFF).detailRes(),
        )
        assertEquals(
            R.string.setup_fix_physical_activity,
            physicalActivity(SetupState.PROBLEM, SetupDetail.NOT_SET).detailRes(),
        )
        assertEquals(R.string.setup_item_physical_activity, SetupItem.PHYSICAL_ACTIVITY.labelRes())
    }

    @Test
    fun `a Restricted battery setting is worded for the phone it is on`() {
        fun restricted(detail: SetupDetail) = SetupRow(
            SetupItem.BATTERY_EXEMPTION,
            SetupState.PROBLEM,
            detail,
            SetupFix.Open(SystemScreen.APP_DETAILS),
        )

        val stockAndroid = restricted(SetupDetail.BATTERY_RESTRICTED).detailRes()
        val hyperOs = restricted(SetupDetail.BATTERY_RESTRICTED_HYPEROS).detailRes()

        assertEquals(R.string.setup_detail_battery_restricted, stockAndroid)
        // HyperOS has no "Unrestricted": its sentence names Battery saver, "No restrictions".
        assertEquals(R.string.setup_detail_battery_restricted_hyperos, hyperOs)
        assertNotEquals(stockAndroid, hyperOs)
    }
}
