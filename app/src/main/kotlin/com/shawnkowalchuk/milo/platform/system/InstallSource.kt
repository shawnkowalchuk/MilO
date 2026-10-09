package com.shawnkowalchuk.milo.platform.system

import android.content.Context
import android.content.pm.PackageManager

/** The package of the Google Play Store app, as Android names the app that installed another. */
internal const val GOOGLE_PLAY_STORE = "com.android.vending"

/**
 * True when [installer], the app Android says installed MilO, is Google Play. Null (Android does
 * not say, or MilO was installed over USB) and any other app (a browser, the Files app, adb) are
 * not.
 */
fun isGooglePlay(installer: String?): Boolean = installer == GOOGLE_PLAY_STORE

/**
 * Whether this copy of MilO came from Google Play (ADR-004). The same APK is published on GitHub
 * and on Google Play, signed with the same key; this is how it tells them apart, for the one
 * thing that differs: the Buy me a coffee tile is not shown in the copy from Google Play, whose
 * payments policy does not allow a link that pays the developer outside it.
 *
 * Android names the app that installed MilO last. A copy from GitHub that Google Play has since
 * updated counts as Google Play's.
 */
fun installedFromGooglePlay(context: Context): Boolean = try {
    isGooglePlay(
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName,
    )
} catch (notInstalled: PackageManager.NameNotFoundException) {
    // Cannot happen for MilO's own package. Not from Google Play is the answer that hides nothing.
    false
}
