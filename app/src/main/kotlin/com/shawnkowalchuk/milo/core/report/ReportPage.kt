package com.shawnkowalchuk.milo.core.report

// What a laid-out page of the PDF is made of, and the paper it is laid out on. Pure Kotlin: the
// layout pass decides where every word and rule goes, and the drawing pass (`platform/report/`)
// only carries that out, so the layout is tested without Android.
//
// Every length is in PostScript points, 1/72 of an inch, the unit Android's PdfDocument works
// in. A text size of 10 is a 10 pt font.

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

/**
 * The kinds of text on the report, each with its size in points and its weight.
 */
enum class ReportTextStyle(val size: Float, val bold: Boolean) {
    /** The report's title. */
    TITLE(18f, true),

    /** The label of a heading line, such as "Name". */
    LABEL(10f, false),

    /** The value of a heading line. */
    VALUE(10f, true),

    /** A note: what the report lists, the legend, the words under the signature line. */
    NOTE(9f, false),

    /** A day's heading. */
    DAY(11f, true),

    /** The titles of the columns. */
    COLUMN(8.5f, true),

    /** A trip's times, addresses and kilometres. */
    CELL(9.5f, false),

    /** A day's subtotal. */
    SUM(9.5f, true),

    /** The period's total. */
    TOTAL(11f, true),

    /** The line at the bottom of every page. */
    FOOTER(8f, false),
    ;

    /** From the top of one line to the top of the next. */
    val leading: Float get() = size * LEADING
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
    ) : PageItem {
        override fun movedDown(by: Float): Text = copy(baseline = baseline + by)
    }

    /** A straight rule across the page at height [y], [thickness] points thick. */
    data class Rule(val fromX: Float, val toX: Float, val y: Float, val thickness: Float) :
        PageItem {
        override fun movedDown(by: Float): Rule = copy(y = y + by)
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
