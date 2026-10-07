package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The typeface is one file that the text styles lean on in four ways, and nothing but these
 * tests would notice any of them breaking: the file has to be the one that was approved, it has
 * to be able to draw the four weights, each entry of the family has to ask it for its own
 * weight (without that, bold text is drawn like regular text and no build fails), and the line
 * height written in `Type.kt` has to be the file's own.
 */
class TypefaceTest {
    // Gradle runs the unit tests from the module's folder, app/.
    private val file = File("src/main/res/font/sora.ttf")

    @Test
    fun `the font file is the one the owner approved`() {
        assertTrue("Not found: ${file.absolutePath}", file.isFile)
        val sha256 =
            MessageDigest
                .getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        // Sora[wght].ttf from github.com/google/fonts, ofl/sora, as downloaded on 2026-10-06.
        // A new version of the font is a new look at its licence and at every screen: change
        // this value in that change, not to make the test pass.
        assertEquals("84ff7096ae3ec6c8be47d906d1a0ba4de7f2ce78c615275c77301964a316e16c", sha256)
    }

    @Test
    fun `the file can be drawn at every weight the text styles ask for`() {
        val weightAxis = FontFile(file).weightAxis()

        assertTrue("The font has no weight axis: it is not a variable font", weightAxis != null)
        SoraWeights.forEach { weight ->
            assertTrue(
                "Weight ${weight.weight} is outside the file's $weightAxis",
                weight.weight.toFloat() in weightAxis!!,
            )
        }
    }

    @Test
    fun `each entry of the family asks the file for its own weight`() {
        val entries = (MiloFontFamily as FontListFontFamily).fonts.map { it as ResourceFont }

        assertEquals(SoraWeights, entries.map { it.weight })
        entries.forEach { entry ->
            // "wght" is the name every variable font gives its weight axis.
            val asked = entry.variationSettings.settings.single { it.axisName == "wght" }
            assertEquals(
                "The entry for ${entry.weight}",
                entry.weight.weight.toFloat(),
                asked.toVariationValue(Density(1f)),
                0f,
            )
        }
    }

    @Test
    fun `no text style asks for a weight the family does not have`() {
        // Asked for a fifth weight, Compose would quietly draw one of the four in its place,
        // and the text would not be the weight its style names.
        allStyles().forEach { (name, style) ->
            assertTrue("$name asks for ${style.fontWeight}", style.fontWeight in SoraWeights)
        }
    }

    @Test
    fun `the line height written for Sora is the one the file asks for`() {
        assertEquals(TextUnitType.Em, SoraLine.type)
        assertEquals(FontFile(file).ownLine(), SoraLine.value, 0.001f)
    }

    @Test
    fun `every text style names its own line height, as a share of its text size`() {
        // A style without one takes over the line height of whatever it stands in, and one in
        // sp would not follow a size that a screen changes.
        allStyles().forEach { (name, style) ->
            assertEquals("$name: line height", TextUnitType.Em, style.lineHeight.type)
            assertEquals("$name: letter spacing", TextUnitType.Em, style.letterSpacing.type)
        }
    }

    @Test
    fun `only the two styles of the event log are monospace`() {
        val monospace = allStyles().filter { (_, style) ->
            style.fontFamily == FontFamily.Monospace
        }

        assertEquals(listOf("logTime", "logTag"), monospace.map { it.first })
    }

    private fun allStyles(): List<Pair<String, TextStyle>> {
        val material = MiloTypography
        val own = MiloTextStyles()
        return listOf(
            "displayLarge" to material.displayLarge,
            "displayMedium" to material.displayMedium,
            "displaySmall" to material.displaySmall,
            "headlineLarge" to material.headlineLarge,
            "headlineMedium" to material.headlineMedium,
            "headlineSmall" to material.headlineSmall,
            "titleLarge" to material.titleLarge,
            "titleMedium" to material.titleMedium,
            "titleSmall" to material.titleSmall,
            "bodyLarge" to material.bodyLarge,
            "bodyMedium" to material.bodyMedium,
            "bodySmall" to material.bodySmall,
            "labelLarge" to material.labelLarge,
            "labelMedium" to material.labelMedium,
            "labelSmall" to material.labelSmall,
            "mainButton" to own.mainButton,
            "fieldText" to own.fieldText,
            "sentence" to own.sentence,
            "appName" to own.appName,
            "rowFigure" to own.rowFigure,
            "sideFigure" to own.sideFigure,
            "spanFigure" to own.spanFigure,
            "tileLabel" to own.tileLabel,
            "accentNote" to own.accentNote,
            "markLetter" to own.markLetter,
            "logTime" to own.logTime,
            "logTag" to own.logTag,
        )
    }
}

/**
 * Just enough of the TrueType file format to read three things from a font file: how many units
 * its letters are drawn in, how much room it asks for above and below a line of text, and the
 * range of its weight axis if it has one. A font file starts with a list of named tables, each
 * with the place it starts at; every number in it is stored with its highest byte first.
 */
private class FontFile(file: File) {
    private val bytes: ByteBuffer = ByteBuffer.wrap(file.readBytes())

    // The list of tables: a count at byte 4, then 16 bytes for each (name, checksum, start,
    // length) from byte 12 on.
    private val tables: Map<String, Int> =
        (0 until bytes.getShort(4).toInt()).associate { index ->
            val entry = 12 + 16 * index
            val name = String(ByteArray(4) { bytes.get(entry + it) }, Charsets.US_ASCII)
            name to bytes.getInt(entry + 8)
        }

    /** The line the font asks for, as a share of the text size: 1.26 means 126 percent. */
    fun ownLine(): Float {
        val unitsPerEm = bytes.getShort(tables.getValue("head") + 18).toInt() and 0xFFFF
        val hhea = tables.getValue("hhea")
        val above = bytes.getShort(hhea + 4).toInt()
        val below = bytes.getShort(hhea + 6).toInt()
        val between = bytes.getShort(hhea + 8).toInt()
        return (above - below + between).toFloat() / unitsPerEm
    }

    /** The lightest and the heaviest weight the file can be drawn at, or null if it has none. */
    fun weightAxis(): ClosedFloatingPointRange<Float>? {
        val fvar = tables["fvar"] ?: return null
        val firstAxis = fvar + (bytes.getShort(fvar + 4).toInt() and 0xFFFF)
        val axisCount = bytes.getShort(fvar + 8).toInt() and 0xFFFF
        val axisSize = bytes.getShort(fvar + 10).toInt() and 0xFFFF
        return (0 until axisCount)
            .map { firstAxis + it * axisSize }
            .firstOrNull { axis ->
                String(ByteArray(4) { bytes.get(axis + it) }, Charsets.US_ASCII) ==
                    "wght"
            }
            ?.let { axis -> fixed(axis + 4)..fixed(axis + 12) }
    }

    // A number with a fraction, stored as a whole number of 65,536ths.
    private fun fixed(at: Int): Float = bytes.getInt(at) / 65536f
}
