package com.shawnkowalchuk.milo.platform.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which installer counts as Google Play, the one thing that hides the Buy me a coffee tile
 * (ADR-004). Asking Android which app installed MilO can only be tested on the phone.
 */
class InstallSourceTest {
    @Test
    fun `the Play Store app is Google Play`() {
        assertTrue(isGooglePlay("com.android.vending"))
    }

    @Test
    fun `no installer named is not Google Play`() {
        // What Android says for a build installed over USB from the Mac.
        assertFalse(isGooglePlay(null))
    }

    @Test
    fun `the APK from GitHub, installed by a browser or the Files app, is not Google Play`() {
        assertFalse(isGooglePlay("com.google.android.packageinstaller"))
        assertFalse(isGooglePlay("com.android.chrome"))
        assertFalse(isGooglePlay("com.android.shell"))
        assertFalse(isGooglePlay(""))
    }
}
