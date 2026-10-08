package com.shawnkowalchuk.milo.platform.system

import android.content.Context
import android.content.pm.PackageManager

/** What a version is called where Android cannot say, which never happens for MilO itself. */
private const val UNKNOWN_VERSION = "unknown"

/**
 * The version of MilO on this phone, as `app/build.gradle.kts` set it for the build.
 *
 * @param name the versionName ("0.1.0"): the one the list of changes is kept under.
 * @param code the versionCode: raised by one for every version, never lowered.
 */
data class InstalledVersion(val name: String, val code: Long)

/** Asks Android which version of MilO is installed. */
fun readInstalledVersion(context: Context): InstalledVersion = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    InstalledVersion(name = info.versionName ?: UNKNOWN_VERSION, code = info.longVersionCode)
} catch (notInstalled: PackageManager.NameNotFoundException) {
    // Cannot happen for MilO's own package. A screen is worth more than its version line.
    InstalledVersion(name = UNKNOWN_VERSION, code = 0)
}
