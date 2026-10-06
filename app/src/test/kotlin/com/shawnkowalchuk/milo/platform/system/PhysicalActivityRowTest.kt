package com.shawnkowalchuk.milo.platform.system

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The setup checklist's row for the Physical activity permission, which only the driving alert
 * needs. That its absence raises no warning on the home screen is in `SetupAttentionTest`.
 */
class PhysicalActivityRowTest {
    @Test
    fun `Physical activity missing is a problem whose button asks for that permission`() {
        val row =
            rows(allGood.copy(activityRecognitionGranted = false))
                .row(SetupItem.PHYSICAL_ACTIVITY)

        assertEquals(SetupState.PROBLEM to SetupDetail.NOT_SET, row.state to row.detail)
        assertEquals(
            SetupFix.AskPermission(
                listOf(Manifest.permission.ACTIVITY_RECOGNITION),
                ifNotAsked = SystemScreen.APP_DETAILS,
            ),
            row.fix,
        )
    }

    @Test
    fun `with the driving alert switched off, Physical activity is not asked for`() {
        val missing = allGood.copy(activityRecognitionGranted = false)

        val row = rows(missing, drivingAlertEnabled = false).row(SetupItem.PHYSICAL_ACTIVITY)
        val granted = rows(allGood, drivingAlertEnabled = false).row(SetupItem.PHYSICAL_ACTIVITY)

        // Nothing would use the permission, so the row is in order and offers no button.
        assertEquals(SetupState.OK to SetupDetail.DRIVING_ALERT_OFF, row.state to row.detail)
        assertNull(row.fix)
        // A permission that is granted is said to be granted, whatever the switch says.
        assertEquals(SetupState.OK to SetupDetail.FINE, granted.state to granted.detail)
    }
}
