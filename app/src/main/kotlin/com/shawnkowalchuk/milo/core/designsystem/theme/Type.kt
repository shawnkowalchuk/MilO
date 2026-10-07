package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The one place the app's typeface is named. Every text style below is built on it, so a change
// of typeface is a change of this line and of nothing else.
//
// TODO(debt): the "Bento" design is drawn in Sora (weights 400, 500, 600 and 700). The font file
// is not in the repository, and it was not downloaded without the owner's go-ahead, so MilO is
// set in the phone's own font for now. With the go-ahead: put the file in res/font and name it
// here (FINDINGS_LOG, 2026-10-06).
private val MiloFontFamily: FontFamily = FontFamily.Default

// The typeface of the event log's time and tag. The system's own monospace, for good: a second
// font file for two small labels is not worth carrying.
private val MiloMonoFontFamily: FontFamily = FontFamily.Monospace

// Every style starts from one of Material's own, for the two settings Material gives all its
// text and a bare TextStyle does not have: no extra padding above and below the letters, and a
// line whose text sits in the middle of its height.
private val Base: TextStyle = Typography().bodyLarge

// The design pulls the letters of its large figures and titles together. Written in em, a share
// of the text size, as the design does, so the pull grows with the text.
private val FigureTracking = (-0.04).em
private val HeadlineTracking = (-0.03).em
private val TitleTracking = (-0.02).em
private val NoTracking = 0.em

private fun style(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    family: FontFamily = MiloFontFamily,
) = Base.copy(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = NoTracking,
)

/**
 * The app's text styles in Material's fifteen roles. Screens pick a role
 * (`MaterialTheme.typography.bodyLarge`); sizes and weights are only ever changed here. The
 * sizes are the design's, in sp, so they grow with the phone's font size setting.
 *
 * Which role is which part of the design:
 * - `displayLarge`, `displayMedium`, `displaySmall`: the three largest figures (the kilometres
 *   of the trip being recorded, a month's total, the count on Setup). Their line is as high as
 *   the figure itself, as drawn, so they are for one line of digits. They are for the screens'
 *   own layouts, the next package; nothing uses them yet.
 * - `headlineLarge` and `headlineSmall`: a figure in a tile, larger and smaller (nothing uses
 *   the larger yet). `headlineMedium`: the title of a bottom-bar screen, and in the design the
 *   few words of a hero tile.
 * - `titleLarge`: the title of a screen opened from another, and of a dialog.
 *   `titleMedium`: a value that is chosen (a stepper's value, a time). What is typed has a
 *   style of its own, `fieldText` below.
 *   `titleSmall`: the figure at the end of a row, and in the design a tile's own title.
 * - `bodyLarge`: the main line of a row. `bodyMedium`: the quieter second line, a tile's
 *   label, a note. `bodySmall`: a caption, such as the label above a text field.
 * - `labelLarge`: the words on a small button or a link, which is also what Material gives
 *   its own buttons. `labelMedium`: a chip, a button inside a row, and in the design a pill.
 *   `labelSmall`: a caption that has to stand out (nothing uses it yet).
 *
 * **Why the main line of a row is semi-bold.** The screens set a row's main line in `bodyLarge`
 * and its second line in `bodyMedium`, so those two roles carry the design's 14 at weight 600
 * and 12 at weight 400, and the rows land on the design without a screen being edited. The
 * price: everything else the screens set in `bodyLarge` is semi-bold too, a plain note such as
 * "Reading…" included, until the screens themselves are restyled.
 *
 * A unit after a figure ("km") is set smaller than the figure and, on a tile, in the colour of
 * secondary text. It has no style of its own, because the design's units are sizes that exist:
 * 18, 16, 14 and 13, each at weight 600 like its figure, which are `rowFigure` (below),
 * `titleMedium`, `bodyLarge` and `labelLarge`.
 *
 * The line heights are Material's proportions, not measured from the design, which leaves most
 * text at its typeface's own line. They are to be set against the drawn screens once, when the
 * design's typeface is in (the TODO at the top of this file): a line's height belongs to its
 * typeface.
 */
internal val MiloTypography: Typography =
    Typography(
        displayLarge = style(56, FontWeight.Bold, 56).copy(letterSpacing = FigureTracking),
        displayMedium = style(44, FontWeight.Bold, 44).copy(letterSpacing = FigureTracking),
        displaySmall = style(36, FontWeight.Bold, 36).copy(letterSpacing = FigureTracking),
        headlineLarge = style(30, FontWeight.SemiBold, 36).copy(letterSpacing = HeadlineTracking),
        headlineMedium = style(26, FontWeight.Bold, 32).copy(letterSpacing = HeadlineTracking),
        headlineSmall = style(26, FontWeight.SemiBold, 32).copy(letterSpacing = HeadlineTracking),
        titleLarge = style(22, FontWeight.Bold, 28).copy(letterSpacing = TitleTracking),
        titleMedium = style(16, FontWeight.SemiBold, 22),
        titleSmall = style(15, FontWeight.SemiBold, 20),
        bodyLarge = style(14, FontWeight.SemiBold, 20),
        bodyMedium = style(12, FontWeight.Normal, 18),
        bodySmall = style(11, FontWeight.Normal, 16),
        labelLarge = style(13, FontWeight.SemiBold, 18),
        labelMedium = style(12, FontWeight.SemiBold, 16),
        labelSmall = style(11, FontWeight.SemiBold, 16),
    )

/**
 * The design's text styles that Material's fifteen roles have no room for. Reach them through
 * `MiloTheme.textStyles`.
 *
 * **Three are in use today:** [mainButton], [fieldText] and [sentence]. The other six are the
 * design's styles for parts that come with the screens' own layouts, the next package. Nothing
 * uses them yet, and what is said of each below is what the design sets in it.
 *
 * Seven sizes of the design have no style at all, because nothing that is built is set in
 * them: 28 at weight 600 (the Report screen's figure), 20 at 600 (the distance typed on the
 * edit screen), 16 at 500 ("of 14 ready" on Setup), 15 at 700 (the letter in the app's mark),
 * 14 at 500 (an address that is still being looked up), 13 at 400 (a bare label beside a
 * switch) and 12 at 500 (the small lines on the accent tile). Each is added here with the part
 * that needs it.
 *
 * @param mainButton the words on the one main button of a screen.
 * @param fieldText what is typed into a text field.
 * @param sentence a sentence that stands by itself in a tile or a dialog, a little larger and
 * firmer than a note.
 * @param appName the app's name at the top of Home, and the figure of a small tile.
 * @param rowFigure a figure or a status word that stands alone at the end of a tile's row, and
 * the unit after the largest figure.
 * @param sideFigure a second figure beside a large one: the minutes beside the kilometres.
 * @param spanFigure a span written out in a tile, such as the hours of a work day.
 * @param logTime the time of a line of the event log, in monospace so that times line up.
 * @param logTag the tag that names a line's kind in the event log.
 */
@Immutable
data class MiloTextStyles(
    val mainButton: TextStyle = style(16, FontWeight.Bold, 22),
    val fieldText: TextStyle = style(14, FontWeight.Normal, 20),
    val sentence: TextStyle = style(13, FontWeight.Medium, 18),
    val appName: TextStyle = style(17, FontWeight.SemiBold, 22),
    val rowFigure: TextStyle = style(18, FontWeight.SemiBold, 24),
    val sideFigure: TextStyle = style(20, FontWeight.Bold, 26),
    val spanFigure: TextStyle =
        style(22, FontWeight.SemiBold, 28).copy(letterSpacing = TitleTracking),
    val logTime: TextStyle = style(11, FontWeight.Normal, 16, MiloMonoFontFamily),
    val logTag: TextStyle = style(10, FontWeight.Medium, 14, MiloMonoFontFamily),
)
