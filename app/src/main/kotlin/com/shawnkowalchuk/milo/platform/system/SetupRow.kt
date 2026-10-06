package com.shawnkowalchuk.milo.platform.system

import com.shawnkowalchuk.milo.data.settings.ConfirmedStep

/**
 * One requirement of the setup checklist.
 *
 * @param required true if an automatic trip can fail to start, or be cut short, without it. The
 * home screen warns while a required row is not OK. The other rows are recommended: they are
 * shown on the checklist and never raise the warning.
 * @param xiaomiOnly true for a HyperOS setting, shown only on a Xiaomi, Redmi or POCO phone.
 */
enum class SetupItem(val required: Boolean, val xiaomiOnly: Boolean = false) {
    PRECISE_LOCATION(required = true),
    BACKGROUND_LOCATION(required = true),

    /** Required because the "could not start this trip" warning is a notification. */
    NOTIFICATIONS(required = true),
    NEARBY_DEVICES(required = true),
    LOCATION_SERVICES(required = true),
    TRUCK(required = true),
    BATTERY_EXEMPTION(required = true),

    /** Recommended: the pause only begins after months in which MilO is never opened. */
    UNUSED_APP_PAUSE(required = false),
    BATTERY_SAVER_OFF(required = true),

    /**
     * Recommended: only the driving alert needs it, and the alert is a safety net that notifies.
     * No trip starts by itself, or fails to, because of this permission.
     */
    PHYSICAL_ACTIVITY(required = false),
    XIAOMI_AUTOSTART(required = true, xiaomiOnly = true),
    XIAOMI_BATTERY_SAVER(required = true, xiaomiOnly = true),

    /** Recommended: these switches govern screens, and MilO starts a service, not a screen. */
    XIAOMI_OTHER_PERMISSIONS(required = false, xiaomiOnly = true),
    XIAOMI_RECENTS_LOCK(required = true, xiaomiOnly = true),
}

/** What the checklist knows about a row. The screen draws each state differently. */
enum class SetupState { OK, PROBLEM, UNKNOWN, NEEDS_CONFIRMATION }

/** Which sentence a row shows under its name. The words themselves are in `strings.xml`. */
enum class SetupDetail {
    /** In order, read from the phone. */
    FINE,

    /** Not in order, and the row's button fixes it. */
    NOT_SET,

    /** "Allow all the time" cannot be asked for until precise location is allowed. */
    PRECISE_LOCATION_FIRST,

    /** Android's battery setting for MilO is "Restricted", which blocks the trip service. */
    BATTERY_RESTRICTED,

    /**
     * The same state on a Xiaomi phone. HyperOS has no setting called "Unrestricted": the way
     * out is its own Battery saver choice "No restrictions", so the sentence names that one.
     */
    BATTERY_RESTRICTED_HYPEROS,
    TRUCK_NOT_PAIRED,

    /** The association was removed in the phone's settings: the truck has to be paired again. */
    TRUCK_ASSOCIATION_MISSING,

    /** This phone cannot watch for the truck: only the Bluetooth broadcast can start a trip. */
    TRUCK_NOT_WATCHED,
    TRUCK_CHECK_FAILED,

    /** The first check of the pairing has not finished. Not a problem yet. */
    TRUCK_NOT_CHECKED_YET,

    /** The driving alert is switched off in Settings, so nothing needs the permission. */
    DRIVING_ALERT_OFF,
    AUTOSTART_LOOKS_ON,
    AUTOSTART_LOOKS_OFF,
    AUTOSTART_UNREADABLE,

    /** Shawn said he set it. MilO has no way to check. */
    CONFIRMED,
    NOT_CONFIRMED,
}

/** What a row's button does. */
sealed interface SetupFix {
    /**
     * Ask for runtime permissions with Android's own dialog.
     *
     * @param ifNotAsked the screen to open when Android no longer shows the dialog, which it
     * stops doing after the permission has been refused twice.
     */
    data class AskPermission(val permissions: List<String>, val ifNotAsked: SystemScreen) : SetupFix

    /** Open a screen of the phone's settings. */
    data class Open(val screen: SystemScreen) : SetupFix

    /** Open MilO's own pairing screen. */
    data object OpenPairing : SetupFix
}

/**
 * One row of the checklist, ready to show.
 *
 * @param fix what the row's button does, or null for a row without one.
 * @param confirmStep set on a row Shawn can confirm, or take his confirmation back from.
 * @param confirmedAtMs when he confirmed it, or null if he has not.
 * @param truckName the paired truck's name, on the truck row only.
 */
data class SetupRow(
    val item: SetupItem,
    val state: SetupState,
    val detail: SetupDetail,
    val fix: SetupFix? = null,
    val confirmStep: ConfirmedStep? = null,
    val confirmedAtMs: Long? = null,
    val truckName: String? = null,
)
