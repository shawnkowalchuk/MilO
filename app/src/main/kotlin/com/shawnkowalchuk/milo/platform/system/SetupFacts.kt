package com.shawnkowalchuk.milo.platform.system

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import java.util.Locale

/**
 * What the unofficial check says about HyperOS "Background autostart". Never a certainty: the
 * check is known to report "on" for a switch that is off, and always reports "on" while MIUI
 * optimisation is disabled (docs/research/2026-10-03-miui-background-limits.md, finding 32).
 */
enum class AutostartReading { LOOKS_ON, LOOKS_OFF, UNKNOWN }

/**
 * Everything the setup checklist reads from the phone, in one piece. Read fresh every time the
 * checklist comes to the front: Shawn leaves MilO to change a setting and comes back.
 *
 * @param preflight the five facts that stop a trip from being recorded, read by the same code
 * that checks them before the trip service is started.
 * @param notificationsEnabled whether MilO's notifications are shown at all.
 * @param ignoringBatteryOptimizations Android's battery optimisation exemption ("Unrestricted").
 * @param exemptFromUnusedAppPause whether "Pause app activity if unused" is off for MilO.
 * @param batterySaverOn the phone-wide Battery Saver, which throttles location and background
 * work for every app.
 * @param activityRecognitionGranted the Physical activity permission, which the phone's driving
 * detection needs. Only the driving alert uses it.
 * @param isXiaomi a Xiaomi, Redmi or POCO phone: the HyperOS rows are shown only there.
 * @param autostart read only on a Xiaomi phone; UNKNOWN anywhere else.
 */
data class SetupFacts(
    val preflight: PreflightFacts,
    val notificationsEnabled: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val exemptFromUnusedAppPause: Boolean,
    val batterySaverOn: Boolean,
    val activityRecognitionGranted: Boolean,
    val isXiaomi: Boolean,
    val autostart: AutostartReading,
)

/** The brands that run MIUI or HyperOS. Compared in small letters. */
private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

/**
 * Whether the phone is a Xiaomi, Redmi or POCO device, from what it reports as its manufacturer
 * and brand. A POCO reports the manufacturer "Xiaomi" and the brand "POCO"; either is enough.
 */
fun isXiaomiFamily(manufacturer: String?, brand: String?): Boolean =
    listOfNotNull(manufacturer, brand).any { it.lowercase(Locale.ROOT) in XIAOMI_BRANDS }

/** MIUI's hidden app-op for Autostart, `OP_AUTO_START` (the research, finding 4). */
private const val OP_AUTO_START = 10008

/**
 * Turns the mode of the Autostart app-op into a reading. Only 0 means allowed: MIUI writes 1 or
 * 2 for "off" depending on the version, so "not 0" is the one safe test for off (finding 4).
 *
 * @param mode what the app-op check returned, or null if it could not be called.
 */
fun autostartReadingOf(mode: Int?): AutostartReading = when (mode) {
    null -> AutostartReading.UNKNOWN
    AppOpsManager.MODE_ALLOWED -> AutostartReading.LOOKS_ON
    else -> AutostartReading.LOOKS_OFF
}

/** Reads [SetupFacts] from the phone. Every call asks Android again; nothing is remembered. */
class SetupReader(private val context: Context, private val preflight: TripPreflight) {
    fun read(): SetupFacts {
        val power = context.getSystemService(PowerManager::class.java)
        val isXiaomi = isXiaomiFamily(Build.MANUFACTURER, Build.BRAND)
        return SetupFacts(
            preflight = preflight.facts(),
            // Also false while the notification permission is not granted.
            notificationsEnabled =
                context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
            ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(
                context.packageName,
            ),
            // "Whitelisted" from having permissions revoked is the same switch as "Pause app
            // activity if unused" being off: the one setting covers both.
            exemptFromUnusedAppPause = context.packageManager.isAutoRevokeWhitelisted,
            batterySaverOn = power.isPowerSaveMode,
            activityRecognitionGranted =
                context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
                    PackageManager.PERMISSION_GRANTED,
            isXiaomi = isXiaomi,
            // The app-op exists only on MIUI and HyperOS, so no other phone is asked.
            autostart =
                if (isXiaomi) autostartReadingOf(autostartMode()) else AutostartReading.UNKNOWN,
        )
    }

    /**
     * The mode of MIUI's Autostart app-op for MilO, or null if it cannot be read.
     *
     * There is no public way to ask. `AppOpsManager.checkOpNoThrow(int, int, String)` is a
     * hidden method that Android still lets apps reach by reflection (it is on the "unsupported"
     * list, not the blocked one), and it is what MIUI's own code calls to decide whether an app
     * may be started (the research, findings 4 and 31). Any failure is "unknown", never "on".
     */
    private fun autostartMode(): Int? = try {
        val check =
            AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
        val appOps = context.getSystemService(AppOpsManager::class.java)
        check.invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) as? Int
    } catch (unreachable: ReflectiveOperationException) {
        // The method is gone or refuses to be called on this build. Reported as "unknown".
        null
    } catch (refused: SecurityException) {
        // Android refused the question. Reported as "unknown".
        null
    }
}
