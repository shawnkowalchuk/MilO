package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.QuietExpander
import com.shawnkowalchuk.milo.core.designsystem.component.StepperButton
import com.shawnkowalchuk.milo.core.designsystem.component.StepperRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.component.TitledSwitchRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import kotlin.math.abs

/**
 * What the reminder's tile can ask for.
 *
 * @param onDayStep the plus (later) or the minus (earlier) beside the day of the month.
 */
internal class ReminderActions(
    val onEnabled: (Boolean) -> Unit,
    val onDayStep: (later: Boolean) -> Unit,
)

/** Which ending an English ordinal number takes: 1st, 2nd, 3rd, 4th. */
enum class OrdinalEnding { ST, ND, RD, TH }

/** The ending of [number] written as an ordinal: 1st, 22nd, 3rd, and 11th, 12th, 13th. */
fun ordinalEnding(number: Int): OrdinalEnding {
    val lastTwo = abs(number) % HUNDRED
    return when {
        lastTwo in FIRST_TEEN..LAST_TEEN -> OrdinalEnding.TH
        lastTwo % TEN == 1 -> OrdinalEnding.ST
        lastTwo % TEN == 2 -> OrdinalEnding.ND
        lastTwo % TEN == 3 -> OrdinalEnding.RD
        else -> OrdinalEnding.TH
    }
}

private const val TEN = 10
private const val HUNDRED = 100

/** Eleven, twelve and thirteen end in "th", although they end in 1, 2 and 3. */
private const val FIRST_TEEN = 11
private const val LAST_TEEN = 13

/**
 * The monthly reminder, as the design draws it: its title, a line that says when it comes, and
 * the switch, all of which toggle it. The line names the day that is stored: "Daily from the
 * 1st until the report is sent".
 *
 * The design draws no way to choose that day, and MilO has one: while the reminder is switched
 * on, the day stands under the title between a minus and a plus, like the numbers of the trip
 * rules. Switched off, the tile shows the title and the switch only; the day is kept, and comes
 * back with the switch.
 *
 * What the reminder goes by is put away behind the line at the end of the tile: it is not an
 * alarm for one moment, it comes back every day until the report is recorded as sent.
 */
@Composable
internal fun ReminderTile(state: SettingsUiState.Ready, actions: ReminderActions) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.controlPadding,
    ) {
        TitledSwitchRow(
            title = stringResource(R.string.settings_reminder_title),
            line = stringResource(reminderLineRes(state.reminderDay), state.reminderDay),
            checked = state.reminderEnabled,
            onCheckedChange = actions.onEnabled,
        )
        if (state.reminderEnabled) {
            StepperRow(
                label = stringResource(R.string.settings_reminder_day_label),
                value = stringResource(R.string.settings_reminder_day_value, state.reminderDay),
                decrease =
                    StepperButton(
                        description = stringResource(R.string.settings_reminder_day_earlier),
                        onClick = { actions.onDayStep(false) },
                        enabled = state.canRemindEarlier,
                    ),
                increase =
                    StepperButton(
                        description = stringResource(R.string.settings_reminder_day_later),
                        onClick = { actions.onDayStep(true) },
                        enabled = state.canRemindLater,
                    ),
            )
        }
        QuietExpander(
            label = stringResource(R.string.settings_about_reminder),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
        ) {
            Note(stringResource(R.string.settings_reminder_detail))
            Note(stringResource(R.string.settings_reminder_day_detail))
        }
    }
}

/** The sentence under the title, for the ending the stored day takes as an ordinal. */
private fun reminderLineRes(day: Int): Int = when (ordinalEnding(day)) {
    OrdinalEnding.ST -> R.string.settings_reminder_from_st
    OrdinalEnding.ND -> R.string.settings_reminder_from_nd
    OrdinalEnding.RD -> R.string.settings_reminder_from_rd
    OrdinalEnding.TH -> R.string.settings_reminder_from_th
}
