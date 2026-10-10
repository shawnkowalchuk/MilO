package com.shawnkowalchuk.milo.feature.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The email that the Settings screen's "Email support" tile starts: where it goes, and how its
 * subject and text reach the email app. The tile itself needs a phone; these are its parts
 * that do not.
 */
class SupportTileTest {
    @Test
    fun `the draft goes to the support address, in the form email apps read`() {
        val body = supportBody("MilO Trip Log 0.3.1 (build 4) · Android 16 · Xiaomi 23078PND5G")

        val mailto = supportMailto(SUPPORT_ADDRESS, "MilO Trip Log support", body)

        assertEquals(
            "mailto:support@milotriplog.top?subject=MilO%20Trip%20Log%20support" +
                "&body=%0A%0A%0AMilO%20Trip%20Log%200.3.1%20%28build%204%29%20%C2%B7%20" +
                "Android%2016%20%C2%B7%20Xiaomi%2023078PND5G",
            mailto,
        )
    }

    @Test
    fun `a space is never written as a plus sign, which an email app would show as it stands`() {
        val mailto = supportMailto(SUPPORT_ADDRESS, "a b & c", "1 + 1 = 2?")

        assertTrue(
            mailto,
            mailto.endsWith("?subject=a%20b%20%26%20c&body=1%20%2B%201%20%3D%202%3F"),
        )
    }

    @Test
    fun `the text leaves room to write in above the line about MilO and the phone`() {
        assertEquals("\n\n\nabout", supportBody("about"))
    }

    @Test
    fun `the phone is named by its maker and its model, the maker once`() {
        assertEquals("Xiaomi 23078PND5G", phoneName("Xiaomi", "23078PND5G"))
        assertEquals("Samsung SM-S911W", phoneName("samsung", "SM-S911W"))
        assertEquals("Google Pixel 8", phoneName("Google", "Google Pixel 8"))
        assertEquals("Pixel 8", phoneName("", " Pixel 8 "))
    }

    @Test
    fun `the website's Support link is the address the app writes to`() {
        // Gradle runs the unit tests from the module's folder, app/.
        val page = File("../website/index.html")
        assertTrue("Not found: ${page.absolutePath}", page.isFile)

        assertTrue(page.readText().contains("href=\"mailto:$SUPPORT_ADDRESS"))
    }
}
