package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shawnkowalchuk.milo.R

// The app's typeface is Sora, the one the "Bento" design is drawn in. This is the one place it
// is named: every text style below is built on MiloFontFamily, so a change of typeface is a
// change here and of nothing else.
//
// The file is res/font/sora.ttf. It was taken unchanged from Google's fonts repository
// (github.com/google/fonts, ofl/sora/Sora[wght].ttf) on 2026-10-06, with the owner's go-ahead.
// Its licence, the SIL Open Font License 1.1, is licenses/Sora-OFL.txt in this repository and
// has to stay there for as long as the font does (FINDINGS_LOG, 2026-10-06).
//
// Sora is a variable font: one file that holds every weight from 100 to 800, where most
// typefaces come as one file for each weight. These four are the weights the design uses, and
// the only ones a text style below may ask for (TypefaceTest holds them to that).
internal val SoraWeights: List<FontWeight> =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

// Each entry is the same file, asked for at one weight. Both arguments are needed. `weight`
// tells Compose which entry to pick for a text style. `variationSettings` tells the font how
// heavy to draw itself: without it the file is drawn at its own default, 400, in every entry,
// and bold text looks exactly like regular text. (Font() called without `variationSettings` is
// another function of the same name, and that one sets none.) A screenshot is the only place
// the mistake shows, so TypefaceTest reads the setting back from every entry.
internal val MiloFontFamily: FontFamily =
    FontFamily(
        SoraWeights.map { weight ->
            Font(
                resId = R.font.sora,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

// The typeface of the event log's time and tag. The system's own monospace, for good: a second
// font file for two small labels is not worth carrying. Which monospace that is differs from
// phone to phone, so its two styles are given the same line height as the rest and a line of
// the Log is as high on every phone.
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

// How high a line of text is. Written in em as well, so a line grows with its text, also when
// the phone's font size is turned up.
//
// The design leaves most text at the line its typeface asks for. For Sora that is 1.26 times
// the text size: the font file keeps 0.97 above the line the letters stand on and 0.29 below it
// (TypefaceTest reads both from the file). The number is written out, and not left for Android
// to work out, because a text style that names no line height takes over the one of whatever
// it stands in (a dialog, a button), and the same row would be higher in one place than in
// another.
internal val SoraLine: TextUnit = 1.26.em

// A sentence that runs over several lines needs more air between them than a label does. The
// design gives its 13 sp sentences 1.4 and its 12 sp sentences 1.5.
private val SentenceLine = 1.4.em
private val NoteLine = 1.5.em

// The three largest figures stand on a line exactly as high as the figure, as drawn. A digit
// has nothing that hangs under the line it stands on and is lower than the line is high, so
// nothing of it is cut off. It suits one line of digits, not a sentence.
private val FigureLine = 1.em

private fun style(
    size: Int,
    weight: FontWeight,
    line: TextUnit = SoraLine,
    tracking: TextUnit = NoTracking,
    family: FontFamily = MiloFontFamily,
) = Base.copy(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line,
    letterSpacing = tracking,
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
 *   own layouts, the next package; no screen uses them yet. Material's own clock dialog does:
 *   its two large numbers are `displayLarge`.
 * - `headlineLarge` and `headlineSmall`: a figure in a tile, larger and smaller (no screen uses
 *   the larger yet; Material's calendar sets the chosen date at its top in it).
 *   `headlineMedium`: the title of a bottom-bar screen, and in the design the few words of a
 *   hero tile.
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
 * **Line heights** are the design's (the values are above `style`). Most text stands at Sora's
 * own line. The three largest figures stand on a line as high as themselves. `bodyMedium` has
 * the design's 1.5 for a sentence at 12, because that role is what the screens set their notes
 * and explaining sentences in; the price is that a second line of one line is about 3 sp
 * higher than drawn.
 */
internal val MiloTypography: Typography =
    Typography(
        displayLarge = style(56, FontWeight.Bold, FigureLine, FigureTracking),
        displayMedium = style(44, FontWeight.Bold, FigureLine, FigureTracking),
        displaySmall = style(36, FontWeight.Bold, FigureLine, FigureTracking),
        headlineLarge = style(30, FontWeight.SemiBold, tracking = HeadlineTracking),
        headlineMedium = style(26, FontWeight.Bold, tracking = HeadlineTracking),
        headlineSmall = style(26, FontWeight.SemiBold, tracking = HeadlineTracking),
        titleLarge = style(22, FontWeight.Bold, tracking = TitleTracking),
        titleMedium = style(16, FontWeight.SemiBold),
        titleSmall = style(15, FontWeight.SemiBold),
        bodyLarge = style(14, FontWeight.SemiBold),
        bodyMedium = style(12, FontWeight.Normal, NoteLine),
        bodySmall = style(11, FontWeight.Normal),
        labelLarge = style(13, FontWeight.SemiBold),
        labelMedium = style(12, FontWeight.SemiBold),
        labelSmall = style(11, FontWeight.SemiBold),
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
    val mainButton: TextStyle = style(16, FontWeight.Bold),
    val fieldText: TextStyle = style(14, FontWeight.Normal),
    val sentence: TextStyle = style(13, FontWeight.Medium, SentenceLine),
    val appName: TextStyle = style(17, FontWeight.SemiBold),
    val rowFigure: TextStyle = style(18, FontWeight.SemiBold),
    val sideFigure: TextStyle = style(20, FontWeight.Bold),
    val spanFigure: TextStyle = style(22, FontWeight.SemiBold, tracking = TitleTracking),
    val logTime: TextStyle = style(11, FontWeight.Normal, family = MiloMonoFontFamily),
    val logTag: TextStyle = style(10, FontWeight.Medium, family = MiloMonoFontFamily),
)
