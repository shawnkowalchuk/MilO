package com.shawnkowalchuk.milo.feature.eventlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.Tag
import com.shawnkowalchuk.milo.core.designsystem.component.TilePress
import com.shawnkowalchuk.milo.core.designsystem.component.TileRow
import com.shawnkowalchuk.milo.core.designsystem.component.TileRowSpacing
import com.shawnkowalchuk.milo.core.designsystem.theme.FillAndText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import java.time.ZoneId

// One line of the Log screen, as the owner's design draws it: the time in a column of its own,
// then a small coloured tag and, under the tag, what the line says.

/** The widest a time can be written, in the digits' own width: every digit is as wide. */
private const val WIDEST_TIME = "00:00:00"

/** How wide the column of the times is, on this phone and at its font size. */
@Immutable
internal class LogTimeWidth(val value: Dp)

/**
 * Measures the column of the times: as wide as a time written in the phone's own monospace, so
 * that every line's tag and message start at the same place whatever the font size is.
 */
@Composable
internal fun rememberLogTimeWidth(): LogTimeWidth {
    val measurer = rememberTextMeasurer()
    val style = MiloTheme.textStyles.logTime
    val density = LocalDensity.current
    return remember(measurer, style, density) {
        val pixels = measurer.measure(WIDEST_TIME, style).size.width
        LogTimeWidth(with(density) { pixels.toDp() })
    }
}

/**
 * One line of the log, as a row of its day's tile. A line with something more to show is
 * pressed as a whole, and a screen reader is told what the press does; any line is read as one
 * sentence: its time, its tag and its message.
 */
@Composable
internal fun LogLine(
    line: LogDayLine,
    zone: ZoneId,
    timeWidth: LogTimeWidth,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    val entry = line.entry
    val detail = entry.detail
    val spacing = MiloTheme.spacing
    TileRow(
        place = line.place,
        spacing = TileRowSpacing.DENSE,
        // Only a line with something more to show reacts to a press.
        press =
            detail?.let {
                val words = if (isOpen) R.string.log_detail_hide else R.string.log_detail_show
                TilePress(label = stringResource(words), onClick = onToggle)
            },
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(spacing.tileGap),
        ) {
            Text(
                text = formatLogClock(entry.atMs, zone),
                // The design sets the time a little lower, level with the word in the tag.
                modifier = Modifier.width(timeWidth.value).padding(top = spacing.textGap),
                style = MiloTheme.textStyles.logTime,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                Tag(
                    text = entry.category.tagWord(),
                    colors = entry.category.logGroup().tagColors(),
                )
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MiloTheme.colors.logText,
                )
                if (detail != null) {
                    Text(
                        text = if (isOpen) detail else stringResource(R.string.log_has_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The colours of a line's tag: those of the chip the line is found under, and the design's
 * grey for every line that is under "All" only.
 */
@Composable
private fun LogGroup?.tagColors(): FillAndText {
    val tags = MiloTheme.colors.logTags
    return when (this) {
        LogGroup.TRIPS -> tags.trip
        LogGroup.BLUETOOTH -> tags.bluetooth
        LogGroup.ANDROID_AUTO -> tags.androidAuto
        LogGroup.ERRORS -> tags.error
        null -> tags.service
    }
}
