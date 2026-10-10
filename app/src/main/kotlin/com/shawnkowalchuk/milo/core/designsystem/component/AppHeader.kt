package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatShortDay
import com.shawnkowalchuk.milo.core.util.localDateOf
import java.time.ZoneId

/** How large the design draws the app's mark. */
private val MarkSize = 36.dp

/**
 * Android's smallest target for a finger. The top line is never lower, so it is as high on a
 * screen with the way back as on one without, and the screens do not jump when one is opened.
 */
private val TopLineHeight = 48.dp

/** The arrowhead before the screen's name, as large as the icon of a square button. */
private val ChevronSize = 20.dp

/**
 * The top line of every screen (Shawn's decision of 2026-10-07: "the top MilO icon, name and
 * date on the top left of the screen stays on all interfaces"). At the start, the app's mark
 * (its initial on a square of the accent colour), its name and today's date under it. At the
 * end, the name of the screen; on a screen that was opened from another one, an arrowhead
 * before the name, and the two are one button that goes back.
 *
 * The screen's name is the heading a screen reader announces. The mark is decoration, and the
 * app's name and the date are read as one line.
 *
 * @param title the screen's name. It may take two lines rather than squeeze the app's name.
 * @param onBack pass it on a screen opened from another one: the name is then the way back.
 * The four screens of the bottom bar have none, because the bar is how they are left.
 * @param line a quieter line under the screen's name: who or what the screen is for.
 * @param titleShown false where the end of the line stays empty: on Home since 2026-10-09
 * (Shawn: "remove the home top right corner"), where the bottom bar's lit Home button already
 * says which screen it is. The name is then not drawn; [line] and [onBack] are not used.
 */
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    line: String? = null,
    titleShown: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = TopLineHeight),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.tileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark()
        AppNameAndDay()
        // The rest of the line, with the screen's name at its end.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            when {
                !titleShown -> Unit
                onBack == null -> ScreenName(title, line, MaterialTheme.colorScheme.onSurface)
                else -> BackToPrevious(title, line, onBack)
            }
        }
    }
}

/** The app's initial on a square of the accent colour. */
@Composable
private fun AppMark() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(MarkSize)
                .background(scheme.primary, MiloTheme.shapes.appMark)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_mark),
            style = MiloTheme.textStyles.markLetter,
            color = scheme.onPrimary,
        )
    }
}

/** "MilO" over today's date, which is read again each time MilO is looked at. */
@Composable
private fun AppNameAndDay() {
    val locale = LocalConfiguration.current.locales[0]
    // The day by MilO's clock, not the phone's: the date in the header must not jump while the
    // phone's date is being played with (ADR-006).
    val clock = LocalMiloClock.current
    // MilO can stay open past midnight, and Android says nothing when the day changes.
    var today by remember { mutableStateOf(localDateOf(clock(), ZoneId.systemDefault())) }
    CameToFrontEffect { today = localDateOf(clock(), ZoneId.systemDefault()) }
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = stringResource(R.string.app_name), style = MiloTheme.textStyles.appName)
        Text(
            text = formatShortDay(today, locale),
            style = MiloTheme.textStyles.tileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The screen's name, at the end of the line, with the quieter [line] under it if there is one. */
@Composable
private fun ScreenName(title: String, line: String?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = title,
            style = MiloTheme.textStyles.appName,
            color = color,
            textAlign = TextAlign.End,
            // Lets a screen reader announce where the user has landed.
            modifier = Modifier.semantics { heading() },
        )
        if (line != null) {
            Text(
                text = line,
                style = MiloTheme.textStyles.tileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * The arrowhead and the screen's name, as one button that goes back. The arrowhead is the
 * accent, as the design marks what is pressed; while it is pressed, the arrowhead and the
 * name are the lighter accent, as a link is. No ripple: it would show the whole place a finger
 * can hit, which is higher than the words.
 */
@Composable
private fun BackToPrevious(title: String, line: String?, onBack: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val accent = if (pressed) MiloTheme.colors.accentPressed else MaterialTheme.colorScheme.primary
    Row(
        modifier =
            Modifier
                .heightIn(min = TopLineHeight)
                .clickable(
                    interactionSource = presses,
                    indication = null,
                    onClickLabel = stringResource(R.string.navigate_back_press),
                    role = Role.Button,
                    onClick = onBack,
                ),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiloIcons.Back,
            // The name next to it says where the button is; the press label says what it does.
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(ChevronSize),
        )
        ScreenName(
            title = title,
            line = line,
            color = if (pressed) accent else MaterialTheme.colorScheme.onSurface,
            // Takes what room is left and no more, so a long name breaks rather than pushes.
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun AppHeaderPreview() {
    MiloTheme {
        Surface {
            Column {
                AppHeader(title = "Trips")
                AppHeader(title = "September report", onBack = {}, line = "For the accountant")
            }
        }
    }
}
