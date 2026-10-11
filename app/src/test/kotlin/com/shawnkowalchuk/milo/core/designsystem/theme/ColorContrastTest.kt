package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text colour of the theme against every fill it is written on, held to the contrast the
 * accessibility guidelines (WCAG 2) ask for. The colours are the owner's design and are changed
 * in one file; this is what says so when a change makes something hard to read, before it is
 * found in sunlight on the dashboard.
 */
class ColorContrastTest {
    private val scheme = MiloColorScheme
    private val colors = MiloExtraColors
    private val status = MiloStatus

    /** Every fill that text or a mark is put on, by the name used in a failure message. */
    private val fills =
        mapOf(
            "the page" to scheme.background,
            "a tile" to scheme.surfaceContainer,
            "a control" to colors.control.fill,
            "a text field" to colors.fieldFill,
            "a quiet fill" to colors.quietFill,
        )

    @Test
    fun `ordinary text can be read on every fill`() {
        val texts =
            mapOf(
                "main text" to scheme.onSurface,
                "secondary text" to scheme.onSurfaceVariant,
                "log text" to colors.logText,
                "a link in the accent" to scheme.primary,
                "a pressed link" to colors.accentPressed,
                "red text" to scheme.error,
            )
        for ((textName, text) in texts) {
            for ((fillName, fill) in fills) {
                assertReadable("$textName on $fillName", text, fill, TEXT)
            }
        }
    }

    @Test
    fun `the words on a coloured fill can be read`() {
        val pairs =
            mapOf(
                "the main button" to FillAndText(scheme.primary, scheme.onPrimary),
                "a control" to colors.control,
                "the attention tile's title" to colors.attentionTile,
                "the attention tile's second line" to
                    FillAndText(colors.attentionTile.fill, colors.attentionSecondaryText),
                "the attention tile's button" to colors.attentionButton,
                "a button that takes something away" to colors.danger,
                "the chosen chip" to colors.chipSelected,
                "any other chip" to colors.chip,
                "the log's trip tag" to colors.logTags.trip,
                "the log's Bluetooth tag" to colors.logTags.bluetooth,
                "the log's Android Auto tag" to colors.logTags.androidAuto,
                "the log's service tag" to colors.logTags.service,
                "the log's error tag" to colors.logTags.error,
                "a wheel's digit" to colors.wheels.whole,
                "the tenth's wheel at rest" to colors.wheels.tenth,
                "the tenth's wheel while it counts" to colors.wheels.tenthCounting,
            )
        for ((name, pair) in pairs) {
            assertReadable(name, pair.text, pair.fill, TEXT)
        }
    }

    @Test
    fun `Material's own pairs can be read, for the stock components that use them`() {
        val pairs =
            mapOf(
                "primary" to FillAndText(scheme.primary, scheme.onPrimary),
                "primaryContainer" to
                    FillAndText(scheme.primaryContainer, scheme.onPrimaryContainer),
                "secondary" to FillAndText(scheme.secondary, scheme.onSecondary),
                "secondaryContainer" to
                    FillAndText(scheme.secondaryContainer, scheme.onSecondaryContainer),
                "tertiary" to FillAndText(scheme.tertiary, scheme.onTertiary),
                "tertiaryContainer" to
                    FillAndText(scheme.tertiaryContainer, scheme.onTertiaryContainer),
                "error" to FillAndText(scheme.error, scheme.onError),
                "errorContainer" to FillAndText(scheme.errorContainer, scheme.onErrorContainer),
                "background" to FillAndText(scheme.background, scheme.onBackground),
                "surface" to FillAndText(scheme.surface, scheme.onSurface),
                "surfaceVariant" to FillAndText(scheme.surfaceVariant, scheme.onSurfaceVariant),
                "inverseSurface" to FillAndText(scheme.inverseSurface, scheme.inverseOnSurface),
                "inversePrimary" to FillAndText(scheme.inverseSurface, scheme.inversePrimary),
            )
        for ((name, pair) in pairs) {
            assertReadable(name, pair.text, pair.fill, TEXT)
        }
    }

    @Test
    fun `a status dot stands out from a tile and from the page, and its mark from the dot`() {
        val dots =
            mapOf(
                "ok" to status.ok,
                "problem" to status.problem,
                "unknown" to status.unknown,
                "to confirm" to status.toConfirm,
            )
        for ((name, dot) in dots) {
            assertReadable("the $name dot on a tile", dot, scheme.surfaceContainer, MARK)
            assertReadable("the $name dot on the page", dot, scheme.background, MARK)
        }
        // The fourth dot is an empty ring and has no mark.
        for (name in listOf("ok", "problem", "unknown")) {
            assertReadable("the mark on the $name dot", status.onDot, dots.getValue(name), MARK)
        }
    }

    @Test
    fun `the icons of the bottom bar and the switch's thumb can be made out`() {
        val bar = scheme.surfaceContainer
        assertReadable("the bar's current icon", scheme.primary, bar, MARK)
        assertReadable("the bar's other icons", scheme.onSurfaceVariant, bar, MARK)
        assertReadable("the thumb of a switch that is on", scheme.onPrimary, scheme.primary, MARK)
        assertReadable(
            "the thumb of a switch that is off",
            scheme.onSurfaceVariant,
            colors.control.fill,
            MARK,
        )
        assertReadable("an outline that carries meaning", scheme.outline, bar, MARK)
    }

    @Test
    fun `the tenth's wheel is told apart from the tile it stands on, counting or not`() {
        // Its fill is what says "this digit is the tenth", so the fill itself is held to the
        // measure of a mark. The whole kilometres' cells are not: they only frame their digits.
        val tile = scheme.surfaceContainer
        assertReadable("the tenth's cell at rest", colors.wheels.tenth.fill, tile, MARK)
        assertReadable("the tenth's cell counting", colors.wheels.tenthCounting.fill, tile, MARK)
        assertReadable("the dot of the side that is down", scheme.outline, tile, MARK)
    }

    @Test
    fun `the line around a text field can be made out, whatever state the field is in`() {
        // The line is what shows where a field is, most of all an empty one, so it is held to
        // the same measure as a mark: against the field's own fill inside it, and against the
        // tile or the page outside it.
        val lines =
            mapOf(
                "the line around an idle field" to colors.fieldBorder,
                "the line around the field being typed in" to scheme.primary,
                "the line around a field in error" to scheme.error,
            )
        val grounds =
            mapOf(
                "the field's own fill" to colors.fieldFill,
                "a tile" to scheme.surfaceContainer,
                "the page" to scheme.background,
            )
        for ((lineName, line) in lines) {
            for ((groundName, ground) in grounds) {
                assertReadable("$lineName against $groundName", line, ground, MARK)
            }
        }
    }

    @Test
    fun `the measure itself agrees with the guidelines' own examples`() {
        // Black on white is 21 to 1, the most there is, and a colour on itself is 1 to 1.
        assertTrue(contrast(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contrast(Color.White, Color.Black) in 20.99..21.01)
        assertTrue(contrast(scheme.primary, scheme.primary) in 0.99..1.01)
        // The grey 767676 is the well-known lightest grey that passes on white: 4.54 to 1.
        assertTrue(contrast(Color(0xFF767676), Color.White) in 4.53..4.55)
    }

    private fun assertReadable(name: String, mark: Color, fill: Color, needed: Double) {
        val ratio = contrast(mark, fill)
        assertTrue("$name: %.2f to 1, and it needs $needed".format(ratio), ratio >= needed)
    }

    /** The contrast of two colours as WCAG 2 counts it: from 1 (none) to 21 (black on white). */
    private fun contrast(a: Color, b: Color): Double {
        val lighter = max(luminance(a), luminance(b))
        val darker = min(luminance(a), luminance(b))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        /** What text needs. The guidelines let large text off with less; MilO does not. */
        const val TEXT = 4.5

        /** What an icon, a dot or an outline needs when it carries meaning. */
        const val MARK = 3.0
    }
}
