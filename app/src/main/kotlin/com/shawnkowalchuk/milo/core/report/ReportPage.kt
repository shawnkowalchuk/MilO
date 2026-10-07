package com.shawnkowalchuk.milo.core.report

// What a laid-out page of the PDF is made of, and the paper it is laid out on. Pure Kotlin: the
// layout pass decides where every word and rule goes, and the drawing pass (`platform/report/`)
// only carries that out, so the layout is tested without Android.
//
// Every length is in PostScript points, 1/72 of an inch, the unit Android's PdfDocument works
// in. A text size of 10 is a 10 pt font.
//
// Since 2026-10-07 the report wears the app's look (Shawn: "similar to the app design and
// beautifully designed, not some generic PDF"): the app's typeface, Sora, in the weights the
// screens use, the app's mark and its two main colours on rounded tiles, on white paper.

/** US Letter, upright: 8.5 by 11 inches. */
const val REPORT_PAGE_WIDTH = 612
const val REPORT_PAGE_HEIGHT = 792

// The margins are MilO's own and no content rectangle is set on the page, so a coordinate here
// is a coordinate on the paper. Three quarters of an inch clears the edge no printer reaches.
private const val MARGIN = 54f

/** Where the content of a page begins and ends, from left to right and from top to bottom. */
const val CONTENT_LEFT = MARGIN
const val CONTENT_RIGHT = REPORT_PAGE_WIDTH - MARGIN
const val CONTENT_TOP = MARGIN
const val CONTENT_BOTTOM = REPORT_PAGE_HEIGHT - MARGIN

/** The footer's line stands in the bottom margin, clear of the content above it. */
const val FOOTER_BASELINE = REPORT_PAGE_HEIGHT - 30f

/** A line of text is this much higher than its font size, which keeps lines apart. */
private const val LEADING = 1.3f

/** The weights of Sora the report uses: the four the screens use (`Type.kt`). */
private const val REGULAR = 400
private const val MEDIUM = 500
private const val SEMI_BOLD = 600
private const val BOLD = 700

/**
 * The kinds of text on the report, each with its size in points and its weight, from 100 to
 * 900 as a font names it.
 */
enum class ReportTextStyle(val size: Float, val weight: Int) {
    /** The app's initial, on the lime square of its mark. */
    MARK(16f, BOLD),

    /** The app's name, beside its mark at the top of the first page. */
    BRAND(16f, SEMI_BOLD),

    /** The quieter line under the app's name, and under the period: what this is and when. */
    BRAND_LINE(9f, REGULAR),

    /** The period, large, at the end of the top tile. */
    PERIOD(16f, SEMI_BOLD),

    /** The small label at the top of a tile of figures: "Business", "Personal". */
    TILE_LABEL(9f, SEMI_BOLD),

    /** The kilometres of a tile of figures, large. */
    FIGURE(26f, BOLD),

    /** The unit after a large figure. */
    FIGURE_UNIT(12f, SEMI_BOLD),

    /** The line under a large figure: how many trips it adds up. */
    TILE_LINE(9f, MEDIUM),

    /** The label over a detail of the sender, such as "Name". */
    LABEL(8f, MEDIUM),

    /** The detail under its label. */
    VALUE(10f, SEMI_BOLD),

    /** A note: what the report lists, the legend, the words under the signature line. */
    NOTE(8.5f, REGULAR),

    /** A day's heading. */
    DAY(10f, SEMI_BOLD),

    /** The titles of the columns. */
    COLUMN(7.5f, SEMI_BOLD),

    /** A trip's times, addresses and kilometres. */
    CELL(9f, REGULAR),

    /** A day's subtotal. */
    SUM(9f, SEMI_BOLD),

    /** What the period's total is of. */
    TOTAL_LABEL(11f, SEMI_BOLD),

    /** The period's total. */
    TOTAL(22f, BOLD),

    /** The line at the bottom of every page. */
    FOOTER(7.5f, REGULAR),

    /** The app's initial on the small mark at the start of that line. */
    FOOTER_MARK(6f, BOLD),
    ;

    /** From the top of one line to the top of the next. */
    val leading: Float get() = size * LEADING
}

/**
 * The colours of the report: ink on white paper. Four are the app's own and repeat the theme's
 * tokens (`core/designsystem/theme/Color.kt`), which only Compose can read; `ReportInkTest`
 * keeps them equal. The other three are made for paper, where the app has no colour: the
 * screens are dark, and a dark page would be a page of ink.
 *
 * @param argb the colour as Android's `Color` writes it: alpha, red, green, blue.
 */
enum class ReportInk(val argb: Int) {
    /** The app's page colour: the ink of every word on paper, and the top and total tiles. */
    DARK(0xFF121316.toInt()),

    /** The app's accent: its mark, the Business tile, the total on its dark tile. */
    ACCENT(0xFFC6F432.toInt()),

    /** The app's main text colour, for words on a dark tile. */
    ON_DARK(0xFFF2F3F5.toInt()),

    /** The app's quieter text colour, for the quieter words on a dark tile. */
    ON_DARK_QUIET(0xFF9298A3.toInt()),

    /** Quieter words on paper: labels, column titles, notes. 5.9 to 1 on white. */
    QUIET(0xFF5F6570.toInt()),

    /** A tile on paper, a shade darker than the page, as the app's tiles are a shade lighter. */
    PANEL(0xFFF1F2F4.toInt()),

    /** The rules between a day's trips. */
    HAIRLINE(0xFFDDE0E5.toInt()),
}

/** One thing drawn on a page. */
sealed interface PageItem {
    /** The same item [by] points further down the page. */
    fun movedDown(by: Float): PageItem

    /**
     * One line of text.
     *
     * @param x where the line starts, or where it ends if it is [rightAligned].
     * @param baseline the line the letters stand on.
     */
    data class Text(
        val text: String,
        val x: Float,
        val baseline: Float,
        val style: ReportTextStyle,
        val rightAligned: Boolean = false,
        val ink: ReportInk = ReportInk.DARK,
    ) : PageItem {
        override fun movedDown(by: Float): Text = copy(baseline = baseline + by)
    }

    /** A straight rule across the page at height [y], [thickness] points thick. */
    data class Rule(
        val fromX: Float,
        val toX: Float,
        val y: Float,
        val thickness: Float,
        val ink: ReportInk = ReportInk.DARK,
    ) : PageItem {
        override fun movedDown(by: Float): Rule = copy(y = y + by)
    }

    /**
     * A filled rectangle with round corners: a tile, the mark's square. Drawn before what
     * stands on it, because the items of a page are drawn in their order.
     */
    data class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val radius: Float,
        val ink: ReportInk,
    ) : PageItem {
        override fun movedDown(by: Float): Box = copy(top = top + by, bottom = bottom + by)
    }
}

/**
 * One page, ready to be drawn.
 *
 * @param number counted from 1.
 */
data class ReportPage(val number: Int, val items: List<PageItem>)

/**
 * How wide a line of text comes out in a style, in points. The drawing pass answers with the
 * very paint it draws with, so that what was measured is what is drawn; a test answers with
 * plain arithmetic.
 */
fun interface TextMeasure {
    fun width(text: String, style: ReportTextStyle): Float
}

/**
 * Breaks [text] into lines no wider than [maxWidth], at its spaces. A single word that is wider
 * than a line by itself (a long street name without a space, a web address) is cut where the
 * line is full, so that nothing ever runs out of its column.
 *
 * @return at least one line; an empty text gives one empty line.
 */
internal fun wrap(
    text: String,
    maxWidth: Float,
    style: ReportTextStyle,
    measure: TextMeasure,
): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in text.split(' ').filter { it.isNotEmpty() }) {
        val longer = if (line.isEmpty()) word else "$line $word"
        if (measure.width(longer, style) <= maxWidth) {
            line = longer
            continue
        }
        if (line.isNotEmpty()) lines += line
        var rest = word
        while (measure.width(rest, style) > maxWidth && rest.length > 1) {
            val cut = fittingLength(rest, maxWidth, style, measure)
            lines += rest.substring(0, cut)
            rest = rest.substring(cut)
        }
        line = rest
    }
    lines += line
    return lines
}

/**
 * How many characters of [word] fit in [maxWidth]: at least one, so that a column too narrow
 * for a single letter still makes progress, and never so as to cut a character that is stored
 * as two (an emoji) in half.
 */
private fun fittingLength(
    word: String,
    maxWidth: Float,
    style: ReportTextStyle,
    measure: TextMeasure,
): Int {
    var length = 1
    while (length < word.length) {
        if (measure.width(word.substring(0, length + 1), style) > maxWidth) break
        length++
    }
    if (length < word.length && Character.isLowSurrogate(word[length])) {
        length = if (length > 1) length - 1 else length + 1
    }
    return length
}
