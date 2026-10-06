package com.shawnkowalchuk.milo.platform.system

import android.annotation.SuppressLint
import android.provider.Settings

/** A screen outside MilO that a button of the setup checklist or the pairing screen opens. */
enum class SystemScreen {
    /** Android's page for MilO itself. Every other screen falls back on it. */
    APP_DETAILS,
    APP_NOTIFICATIONS,
    LOCATION,
    BLUETOOTH,

    /** Android's dialog that asks to let MilO run without battery optimisation. */
    BATTERY_EXEMPTION,

    /** The page with the switch "Pause app activity if unused". */
    UNUSED_APP_PAUSE,
    BATTERY_SAVER,

    /** HyperOS: the list of apps allowed to start in the background. */
    XIAOMI_AUTOSTART,

    /** HyperOS: MilO's own Battery saver profile ("No restrictions"). */
    XIAOMI_BATTERY_SAVER,

    /** HyperOS: MilO's "Other permissions". */
    XIAOMI_OTHER_PERMISSIONS,
}

/** What MilO tells a settings screen about itself. */
data class AppIdentity(val packageName: String, val uid: Int, val label: String)

/**
 * One way of opening a screen, as plain values, so the list of ways can be tested without a
 * phone. [SystemScreens] turns it into an Android `Intent`.
 *
 * @param component a package and a class name, for a screen that is addressed directly.
 * @param targetPackage restricts an [action] to one app.
 * @param data the address the intent carries, such as `package:com.shawnkowalchuk.milo`.
 */
data class ScreenIntent(
    val action: String? = null,
    val component: Pair<String, String>? = null,
    val targetPackage: String? = null,
    val data: String? = null,
    val stringExtras: Map<String, String> = emptyMap(),
    val intExtras: Map<String, Int> = emptyMap(),
)

// The HyperOS screens. None of them is documented by Xiaomi beyond the two action names; the
// component names and the extras are what maintained open-source apps use. Sources and how far
// each was confirmed: docs/research/2026-10-03-miui-background-limits.md, findings 27 to 30.
private const val MIUI_SECURITY_CENTER = "com.miui.securitycenter"
private const val MIUI_AUTOSTART_ACTIVITY =
    "com.miui.permcenter.autostart.AutoStartManagementActivity"
private const val MIUI_AUTOSTART_ACTION = "miui.intent.action.OP_AUTO_START"
private const val MIUI_POWER_KEEPER = "com.miui.powerkeeper"
private const val MIUI_BATTERY_SAVER_ACTIVITY = "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
private const val MIUI_PERMISSION_EDITOR_ACTION = "miui.intent.action.APP_PERM_EDITOR"

/** `Intent.ACTION_AUTO_REVOKE_PERMISSIONS`, written out so this file needs no Android class. */
private const val ACTION_AUTO_REVOKE_PERMISSIONS = "android.intent.action.AUTO_REVOKE_PERMISSIONS"

/**
 * The ways of opening [screen], best first. They are tried in order and the first one the phone
 * accepts is used.
 *
 * **The last entry is always Android's own page for MilO.** HyperOS builds remove and rename
 * their settings screens, so a missing one must end somewhere useful and never in a crash. On
 * HyperOS that page is Xiaomi's "App info", which has Autostart, Battery saver and Other
 * permissions one tap away (finding 30).
 */
// Lint flags the request to be exempt from battery optimisation because Google Play restricts
// it. MilO is installed from Android Studio on one phone and is never published there, and a
// trip-detection app is what the exemption exists for (ADR-002).
@SuppressLint("BatteryLife")
fun screenIntents(screen: SystemScreen, app: AppIdentity): List<ScreenIntent> {
    val packageAddress = "package:${app.packageName}"
    val appDetails =
        ScreenIntent(
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            data = packageAddress,
        )
    val preferred =
        when (screen) {
            SystemScreen.APP_DETAILS -> emptyList()

            SystemScreen.APP_NOTIFICATIONS ->
                listOf(
                    ScreenIntent(
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                        stringExtras = mapOf(Settings.EXTRA_APP_PACKAGE to app.packageName),
                    ),
                )

            SystemScreen.LOCATION ->
                listOf(ScreenIntent(action = Settings.ACTION_LOCATION_SOURCE_SETTINGS))

            SystemScreen.BLUETOOTH ->
                listOf(ScreenIntent(action = Settings.ACTION_BLUETOOTH_SETTINGS))

            SystemScreen.BATTERY_EXEMPTION ->
                listOf(
                    ScreenIntent(
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        data = packageAddress,
                    ),
                    // The list of every app's setting, should the dialog be missing.
                    ScreenIntent(action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )

            SystemScreen.UNUSED_APP_PAUSE ->
                listOf(ScreenIntent(action = ACTION_AUTO_REVOKE_PERMISSIONS, data = packageAddress))

            SystemScreen.BATTERY_SAVER ->
                listOf(ScreenIntent(action = Settings.ACTION_BATTERY_SAVER_SETTINGS))

            SystemScreen.XIAOMI_AUTOSTART ->
                listOf(
                    ScreenIntent(component = MIUI_SECURITY_CENTER to MIUI_AUTOSTART_ACTIVITY),
                    // Xiaomi's documented name for the same list.
                    ScreenIntent(action = MIUI_AUTOSTART_ACTION),
                )

            SystemScreen.XIAOMI_BATTERY_SAVER ->
                listOf(
                    ScreenIntent(
                        component = MIUI_POWER_KEEPER to MIUI_BATTERY_SAVER_ACTIVITY,
                        stringExtras =
                            mapOf("package_name" to app.packageName, "package_label" to app.label),
                    ),
                )

            SystemScreen.XIAOMI_OTHER_PERMISSIONS ->
                listOf(
                    ScreenIntent(
                        action = MIUI_PERMISSION_EDITOR_ACTION,
                        targetPackage = MIUI_SECURITY_CENTER,
                        stringExtras = mapOf("extra_pkgname" to app.packageName),
                        intExtras = mapOf("extra_package_uid" to app.uid),
                    ),
                )
        }
    return preferred + appDetails
}
