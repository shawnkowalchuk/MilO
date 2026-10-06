package com.shawnkowalchuk.milo.feature.setup

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.platform.system.SetupDetail
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SetupItem
import com.shawnkowalchuk.milo.platform.system.SetupRow
import com.shawnkowalchuk.milo.platform.system.SetupState

// Which words each row of the checklist shows. Only the choice is made here: the words
// themselves are in strings.xml.

internal fun SetupState.asRowStatus(): RowStatus = when (this) {
    SetupState.OK -> RowStatus.OK
    SetupState.PROBLEM -> RowStatus.PROBLEM
    SetupState.UNKNOWN -> RowStatus.UNKNOWN
    SetupState.NEEDS_CONFIRMATION -> RowStatus.NEEDS_CONFIRMATION
}

internal fun SetupItem.labelRes(): Int = when (this) {
    SetupItem.PRECISE_LOCATION -> R.string.setup_item_precise_location
    SetupItem.BACKGROUND_LOCATION -> R.string.setup_item_background_location
    SetupItem.NOTIFICATIONS -> R.string.setup_item_notifications
    SetupItem.NEARBY_DEVICES -> R.string.setup_item_nearby_devices
    SetupItem.LOCATION_SERVICES -> R.string.setup_item_location_services
    SetupItem.TRUCK -> R.string.setup_item_truck
    SetupItem.BATTERY_EXEMPTION -> R.string.setup_item_battery_exemption
    SetupItem.UNUSED_APP_PAUSE -> R.string.setup_item_unused_app_pause
    SetupItem.BATTERY_SAVER_OFF -> R.string.setup_item_battery_saver_off
    SetupItem.XIAOMI_AUTOSTART -> R.string.setup_item_xiaomi_autostart
    SetupItem.XIAOMI_BATTERY_SAVER -> R.string.setup_item_xiaomi_battery_saver
    SetupItem.XIAOMI_OTHER_PERMISSIONS -> R.string.setup_item_xiaomi_other_permissions
    SetupItem.XIAOMI_RECENTS_LOCK -> R.string.setup_item_xiaomi_recents_lock
}

/**
 * The sentence under a row's name. A few details read the same on every row; the rest depend on
 * the row. The truck's sentences take the truck's name, and the confirmed one takes a date.
 */
internal fun SetupRow.detailRes(): Int = when (detail) {
    SetupDetail.CONFIRMED -> R.string.setup_detail_confirmed
    SetupDetail.PRECISE_LOCATION_FIRST -> R.string.setup_detail_precise_location_first
    SetupDetail.BATTERY_RESTRICTED -> R.string.setup_detail_battery_restricted
    SetupDetail.BATTERY_RESTRICTED_HYPEROS -> R.string.setup_detail_battery_restricted_hyperos
    SetupDetail.TRUCK_NOT_PAIRED -> R.string.setup_detail_truck_not_paired
    SetupDetail.TRUCK_ASSOCIATION_MISSING -> R.string.setup_detail_truck_association_missing
    SetupDetail.TRUCK_NOT_WATCHED -> R.string.setup_detail_truck_not_watched
    SetupDetail.TRUCK_CHECK_FAILED -> R.string.setup_detail_truck_check_failed
    SetupDetail.TRUCK_NOT_CHECKED_YET -> R.string.setup_detail_checking
    SetupDetail.AUTOSTART_LOOKS_ON -> R.string.setup_detail_autostart_looks_on
    SetupDetail.AUTOSTART_LOOKS_OFF -> R.string.setup_detail_autostart_looks_off
    SetupDetail.AUTOSTART_UNREADABLE -> R.string.setup_detail_autostart_unreadable
    SetupDetail.FINE -> item.fineRes()
    SetupDetail.NOT_SET, SetupDetail.NOT_CONFIRMED -> item.notSetRes()
}

private fun SetupItem.fineRes(): Int = when (this) {
    SetupItem.BACKGROUND_LOCATION -> R.string.setup_fine_background_location
    SetupItem.LOCATION_SERVICES -> R.string.setup_fine_location_services
    SetupItem.TRUCK -> R.string.setup_fine_truck
    SetupItem.BATTERY_EXEMPTION -> R.string.setup_fine_battery_exemption
    SetupItem.UNUSED_APP_PAUSE -> R.string.setup_fine_unused_app_pause
    SetupItem.BATTERY_SAVER_OFF -> R.string.setup_fine_battery_saver_off
    else -> R.string.setup_fine_allowed
}

private fun SetupItem.notSetRes(): Int = when (this) {
    SetupItem.PRECISE_LOCATION -> R.string.setup_fix_precise_location

    SetupItem.BACKGROUND_LOCATION -> R.string.setup_fix_background_location

    SetupItem.NOTIFICATIONS -> R.string.setup_fix_notifications

    SetupItem.NEARBY_DEVICES -> R.string.setup_fix_nearby_devices

    SetupItem.LOCATION_SERVICES -> R.string.setup_fix_location_services

    SetupItem.BATTERY_EXEMPTION -> R.string.setup_fix_battery_exemption

    SetupItem.UNUSED_APP_PAUSE -> R.string.setup_fix_unused_app_pause

    SetupItem.BATTERY_SAVER_OFF -> R.string.setup_fix_battery_saver_off

    SetupItem.XIAOMI_BATTERY_SAVER -> R.string.setup_fix_xiaomi_battery_saver

    SetupItem.XIAOMI_OTHER_PERMISSIONS -> R.string.setup_fix_xiaomi_other_permissions

    SetupItem.XIAOMI_RECENTS_LOCK -> R.string.setup_fix_xiaomi_recents_lock

    // These two always carry a detail of their own and never reach this function.
    SetupItem.TRUCK -> R.string.setup_detail_truck_not_paired

    SetupItem.XIAOMI_AUTOSTART -> R.string.setup_detail_autostart_unreadable
}

/**
 * The truck row's states in which there is no pairing to change: nothing was ever paired, or
 * Android dropped the association and the sentence says "pair it again".
 */
private val NOTHING_PAIRED_DETAILS =
    setOf(SetupDetail.TRUCK_NOT_PAIRED, SetupDetail.TRUCK_ASSOCIATION_MISSING)

/** The words on the button that carries out a row's fix. */
internal fun SetupRow.fixLabelRes(): Int = when (fix) {
    is SetupFix.AskPermission -> R.string.setup_action_allow

    is SetupFix.Open -> R.string.setup_action_open_settings

    // The button has to say what the sentence beside it says.
    SetupFix.OpenPairing ->
        if (detail in NOTHING_PAIRED_DETAILS) {
            R.string.setup_action_pair_truck
        } else {
            R.string.setup_action_change_truck
        }

    null -> error("A row without a fix has no button")
}
