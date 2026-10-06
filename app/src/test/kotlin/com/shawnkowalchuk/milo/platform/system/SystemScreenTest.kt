package com.shawnkowalchuk.milo.platform.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ways each settings screen is opened, as plain values. Whether a phone has the screen, and
 * what it shows, can only be tested on the phone.
 */
class SystemScreenTest {
    private val milo =
        AppIdentity(packageName = "com.shawnkowalchuk.milo", uid = 10_234, label = "MilO")

    private val appDetails =
        ScreenIntent(
            action = "android.settings.APPLICATION_DETAILS_SETTINGS",
            data = "package:com.shawnkowalchuk.milo",
        )

    @Test
    fun `every screen falls back on Android's own page for MilO`() {
        for (screen in SystemScreen.entries) {
            assertEquals("$screen", appDetails, screenIntents(screen, milo).last())
        }
    }

    @Test
    fun `every screen but the app page itself has a way of its own to try first`() {
        for (screen in SystemScreen.entries - SystemScreen.APP_DETAILS) {
            assertTrue("$screen", screenIntents(screen, milo).size >= 2)
        }
        assertEquals(listOf(appDetails), screenIntents(SystemScreen.APP_DETAILS, milo))
    }

    @Test
    fun `the Autostart list is opened by its component, then by Xiaomi's documented action`() {
        val ways = screenIntents(SystemScreen.XIAOMI_AUTOSTART, milo)

        assertEquals(
            listOf(
                ScreenIntent(
                    component =
                        "com.miui.securitycenter" to
                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
                ScreenIntent(action = "miui.intent.action.OP_AUTO_START"),
                appDetails,
            ),
            ways,
        )
    }

    @Test
    fun `the Battery saver page is told which app it is for`() {
        val first = screenIntents(SystemScreen.XIAOMI_BATTERY_SAVER, milo).first()

        assertEquals(
            ScreenIntent(
                component =
                    "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                stringExtras =
                    mapOf("package_name" to "com.shawnkowalchuk.milo", "package_label" to "MilO"),
            ),
            first,
        )
    }

    @Test
    fun `Other permissions is asked of the Security app, with MilO's package and uid`() {
        val first = screenIntents(SystemScreen.XIAOMI_OTHER_PERMISSIONS, milo).first()

        assertEquals(
            ScreenIntent(
                action = "miui.intent.action.APP_PERM_EDITOR",
                targetPackage = "com.miui.securitycenter",
                stringExtras = mapOf("extra_pkgname" to "com.shawnkowalchuk.milo"),
                intExtras = mapOf("extra_package_uid" to 10_234),
            ),
            first,
        )
    }

    @Test
    fun `the battery exemption is Android's request dialog for MilO, then the list of all apps`() {
        val ways = screenIntents(SystemScreen.BATTERY_EXEMPTION, milo)

        assertEquals(
            listOf(
                ScreenIntent(
                    action = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                    data = "package:com.shawnkowalchuk.milo",
                ),
                ScreenIntent(action = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"),
                appDetails,
            ),
            ways,
        )
    }

    @Test
    fun `the pause-if-unused switch is opened on MilO's own page`() {
        assertEquals(
            ScreenIntent(
                action = "android.intent.action.AUTO_REVOKE_PERMISSIONS",
                data = "package:com.shawnkowalchuk.milo",
            ),
            screenIntents(SystemScreen.UNUSED_APP_PAUSE, milo).first(),
        )
    }
}
