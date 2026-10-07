package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// This file is where the app's colour values are written.
// Screens and components ask for a colour by its role (MaterialTheme.colorScheme.primary,
// MiloTheme.colors.danger, MiloTheme.statusColors.ok), never by its value, so a colour change
// happens here once.
//
// MilO has one look, and it is dark: the "Bento" design the owner supplied on 2026-10-06 has no
// light variant, so there is no second scheme and the phone's light or dark setting changes
// nothing (FINDINGS_LOG, 2026-10-06).
//
// There are four exceptions, each explained where it is written:
//   - the launcher icon drawables (res/drawable/ic_launcher_*.xml), which the launcher draws
//     outside Compose and so cannot read these tokens;
//   - the notification icon (res/drawable/ic_stat_trip.xml), which Android draws itself, outside
//     Compose, and tints as it likes;
//   - the window's background (res/values/colors.xml, used by res/values/themes.xml), which
//     Android paints before Compose has drawn anything. It repeats the page colour below so that
//     a launch never flashes white, and a unit test (WindowBackgroundTest) keeps the two equal;
//   - the placeholder stroke in component/MiloIcons.kt, which Icon() always replaces with a tint.

// The design's colours. Each value is written once, here, and given to one or more roles below.
private val Page = Color(0xFF121316)
private val Tile = Color(0xFF1E2025)
private val ControlFill = Color(0xFF2C2F36)
private val FieldFill = Color(0xFF17191C)
private val QuietFill = Color(0xFF24262B)
private val IdleOutline = Color(0xFF3A3E46)
private val IdleIcon = Color(0xFF5A5F69)

// The design draws the "to confirm" ring in the idle icon's grey (5A5F69). On a tile that is
// 2.5 to 1, under the 3 to 1 a mark that carries meaning needs, so the ring and Material's
// outline use the same grey made just light enough: 3.0 to 1 on a tile.
//
// The line around a text field is this grey too, for the same reason. The design draws it in the
// control's fill (2C2F36), which is 1.2 to 1 on a tile: with a field that is empty, nothing but
// its small label would then show that there is a place to type in.
private val Ring = Color(0xFF656A76)

// What Material lays over the screen behind a sheet. The design has no such layer; black dims
// a dark page without tinting it.
private val Scrim = Color(0xFF000000)

private val TextMain = Color(0xFFF2F3F5)
private val TextSecondary = Color(0xFF9298A3)
private val TextLog = Color(0xFFD8DBE0)

private val Accent = Color(0xFFC6F432)
private val AccentPressed = Color(0xFFDDFF7A)

// The light that runs along the line between the phone and the truck while the two are
// connected: the design's white at 85 percent.
private val Glint = Color(0xD9FFFFFF)

private val AttentionFill = Color(0xFF2A2416)
private val AttentionText = Color(0xFFFFE7A8)
private val AttentionSecondaryText = Color(0xFFD5C597)

private val DangerFill = Color(0xFF2A1719)
private val DangerText = Color(0xFFFF9C9C)
private val ErrorTagFill = Color(0xFF3A1C1F)
private val ErrorTagText = Color(0xFFFFB3B3)

private val BluetoothTagFill = Color(0xFF2A3350)
private val BluetoothTagText = Color(0xFFC9D6FF)
private val AndroidAutoTagFill = Color(0xFF33294A)
private val AndroidAutoTagText = Color(0xFFDCCBFF)

/**
 * Material's colour roles, given the design's colours, so that a stock Material component lands
 * on the design without being told a colour: a `Button` is the accent with dark words, a
 * `TextButton` is accent words, a `HorizontalDivider` is the design's divider line, a dialog is
 * a tile, the time picker's dial is a control on a tile.
 *
 * Every role is set explicitly. A role left out falls back to Material's purple baseline, which
 * then shows up unannounced the first time a component uses that role. Where the design has no
 * colour for a role, the role gets the nearest colour the design does have, never a new one.
 */
internal val MiloColorScheme: ColorScheme =
    darkColorScheme(
        // The accent: the main button, a link, the chosen radio button, a progress bar's fill,
        // the clock hand and the chosen day in the pickers.
        primary = Accent,
        onPrimary = Page,
        // The chosen hour or minute in the time picker. In the design whatever is chosen is the
        // accent itself, so the "container" is not a paler accent.
        primaryContainer = Accent,
        onPrimaryContainer = Page,
        // For an action on a light bar (a snackbar). The design has none; dark words read there.
        inversePrimary = Page,
        // The light fill with dark words: the chosen filter chip.
        secondary = TextMain,
        onSecondary = Page,
        // A control sitting on a tile: a secondary button, a progress bar's track.
        secondaryContainer = ControlFill,
        onSecondaryContainer = TextMain,
        // The amber "something is waiting for you" tile and its button.
        tertiary = AttentionText,
        onTertiary = AttentionFill,
        tertiaryContainer = AttentionFill,
        onTertiaryContainer = AttentionText,
        background = Page,
        onBackground = TextMain,
        // A bare Surface is the page. A tile asks for surfaceContainer.
        surface = Page,
        onSurface = TextMain,
        surfaceVariant = ControlFill,
        // Secondary text: the quieter second line of a row, a tile's label, an idle icon.
        onSurfaceVariant = TextSecondary,
        // Material mixes this into a surface that is "raised". The design is flat, so the only
        // thing raising may ever do is move the page colour towards the tile colour.
        surfaceTint = Tile,
        inverseSurface = TextMain,
        inverseOnSurface = Page,
        error = DangerText,
        onError = Page,
        errorContainer = DangerFill,
        onErrorContainer = DangerText,
        // An outline that has to be seen to be understood (the ring of an unchosen control).
        outline = Ring,
        // A line that only separates: the divider between two rows of a tile.
        outlineVariant = ControlFill,
        scrim = Scrim,
        surfaceBright = ControlFill,
        surfaceDim = Page,
        // From the well a text field is typed into, up to a control on a tile. The three in the
        // middle are all the tile: Material uses them for bars, menus, sheets and dialogs, and
        // in this design each of those is a tile.
        surfaceContainerLowest = FieldFill,
        surfaceContainerLow = Tile,
        surfaceContainer = Tile,
        surfaceContainerHigh = Tile,
        surfaceContainerHighest = ControlFill,
        // The "fixed" roles are meant to be the same in a light and a dark scheme. MilO has one
        // scheme, so they repeat the pairs above; no component MilO uses reads them.
        primaryFixed = AccentPressed,
        primaryFixedDim = Accent,
        onPrimaryFixed = Page,
        onPrimaryFixedVariant = Page,
        secondaryFixed = TextMain,
        secondaryFixedDim = TextLog,
        onSecondaryFixed = Page,
        onSecondaryFixedVariant = Page,
        tertiaryFixed = AttentionText,
        tertiaryFixedDim = AttentionSecondaryText,
        onTertiaryFixed = AttentionFill,
        onTertiaryFixedVariant = AttentionFill,
    )

/**
 * The colours behind every "is this working?" dot in the app.
 *
 * Material has a role for "error" but none for "all good" or "your turn", so the set lives here
 * rather than borrowing unrelated Material roles. Reach it through `MiloTheme.statusColors`.
 *
 * The four states never differ by colour alone: each dot has its own mark as well (a tick, an
 * exclamation mark, a question mark, or an empty ring), for someone who cannot tell the colours
 * apart, and for sunlight that washes them out.
 *
 * @param ok met: the accent.
 * @param problem not met: the design's red.
 * @param toConfirm the user has to do or confirm something MilO cannot check. An empty ring, so
 * this is a line's colour and not a fill's.
 * @param unknown MilO could not find out. Deliberately colourless: the grey of secondary text.
 * @param onDot the mark drawn on a filled dot.
 */
@Immutable
data class MiloStatusColors(
    val ok: Color,
    val problem: Color,
    val toConfirm: Color,
    val unknown: Color,
    val onDot: Color,
)

internal val MiloStatus =
    MiloStatusColors(
        ok = Accent,
        problem = DangerText,
        toConfirm = Ring,
        unknown = TextSecondary,
        onDot = Page,
    )

/** A fill and the colour of what is written or drawn on it. The two are only ever used together. */
@Immutable
data class FillAndText(val fill: Color, val text: Color)

/**
 * The fill and text of the small tag that names a line's kind on the Log screen. Each kind has
 * its own pair so that a column of lines can be scanned by colour.
 *
 * Nothing uses these yet: the Log screen's lines are restyled with the screens' own layouts,
 * the next package.
 *
 * @param service also the pair for any kind that has no colour of its own.
 */
@Immutable
data class MiloLogTagColors(
    val trip: FillAndText,
    val bluetooth: FillAndText,
    val androidAuto: FillAndText,
    val service: FillAndText,
    val error: FillAndText,
)

/**
 * The design's colours that Material has no role for, or no role with a name that says what
 * they are for. Reach them through `MiloTheme.colors`.
 *
 * **In use today:** [control], [fieldFill], [fieldBorder], [chipSelected] and [chip], and since
 * Home was laid out as the design draws it, [quietFill], [idleOutline], [idleIcon], [linkGlint]
 * and the amber tile's three ([attentionTile], [attentionSecondaryText], [attentionButton]),
 * and since Trips was, [danger]. The others ([logText], [accentPressed], [logTags]) are the
 * design's colours for parts that come with the other screens' own layouts. They are named here
 * so that those parts find them; nothing uses them yet, and what is said of each below is what
 * the design draws with it.
 *
 * @param control a control sitting on a tile: a small button, a stepper's button, a switch's
 * track when it is off.
 * @param quietFill a fill that should hardly be noticed: the circle behind an idle icon.
 * @param fieldFill the inside of a text field, darker than the tile it sits on.
 * @param fieldBorder the hairline around a text field while it is neither typed in nor wrong.
 * Not the design's value, which could not be seen on a tile: the grey of an outline that has to
 * be seen (the comment at the palette above says why).
 * @param idleOutline a dashed or idle outline: decoration, not something to be read.
 * @param idleIcon an icon that is idle, beside something that is switched off. Too faint to be
 * the only sign of a button that cannot be pressed (2.1 to 1 on a control), which is why
 * `SquareIconButton` greys its icon with the colour of secondary text instead. The one place it
 * greys a button is the title's "next" square on the page, as drawn on Trips: there it is
 * 2.5 to 1, and a screen reader is told that the button is switched off.
 * @param logText running text in the event log, a little softer than the main text.
 * @param accentPressed the accent while a link is pressed.
 * @param linkGlint the light that runs along the line between the phone and the truck while
 * they are connected. Decoration: nothing is read from it.
 * @param attentionTile the amber tile for something that is waiting for the user, and its title.
 * @param attentionSecondaryText the quieter line on that tile.
 * @param attentionButton the button on that tile.
 * @param danger an action that takes something away ("Delete").
 * @param chipSelected the filter chip that is in force.
 * @param chip every other filter chip. It is the tile's colour: chips stand on the page.
 */
@Immutable
data class MiloColors(
    val control: FillAndText,
    val quietFill: Color,
    val fieldFill: Color,
    val fieldBorder: Color,
    val idleOutline: Color,
    val idleIcon: Color,
    val logText: Color,
    val accentPressed: Color,
    val linkGlint: Color,
    val attentionTile: FillAndText,
    val attentionSecondaryText: Color,
    val attentionButton: FillAndText,
    val danger: FillAndText,
    val chipSelected: FillAndText,
    val chip: FillAndText,
    val logTags: MiloLogTagColors,
)

internal val MiloExtraColors =
    MiloColors(
        control = FillAndText(fill = ControlFill, text = TextMain),
        quietFill = QuietFill,
        fieldFill = FieldFill,
        fieldBorder = Ring,
        idleOutline = IdleOutline,
        idleIcon = IdleIcon,
        logText = TextLog,
        accentPressed = AccentPressed,
        linkGlint = Glint,
        attentionTile = FillAndText(fill = AttentionFill, text = AttentionText),
        attentionSecondaryText = AttentionSecondaryText,
        attentionButton = FillAndText(fill = AttentionText, text = AttentionFill),
        danger = FillAndText(fill = DangerFill, text = DangerText),
        chipSelected = FillAndText(fill = TextMain, text = Page),
        chip = FillAndText(fill = Tile, text = TextMain),
        logTags =
            MiloLogTagColors(
                trip = FillAndText(fill = Accent, text = Page),
                bluetooth = FillAndText(fill = BluetoothTagFill, text = BluetoothTagText),
                androidAuto = FillAndText(fill = AndroidAutoTagFill, text = AndroidAutoTagText),
                service = FillAndText(fill = ControlFill, text = TextLog),
                error = FillAndText(fill = ErrorTagFill, text = ErrorTagText),
            ),
    )
