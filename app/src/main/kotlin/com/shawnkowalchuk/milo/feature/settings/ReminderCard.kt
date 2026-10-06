package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StepperButton
import com.shawnkowalchuk.milo.core.designsystem.component.StepperRow
import com.shawnkowalchuk.milo.core.designsystem.component.SwitchRow

/**
 * What the reminder's card can ask for.
 *
 * @param onDayStep the plus (later) or the minus (earlier) beside the day of the month.
 */
internal class ReminderActions(
    val onEnabled: (Boolean) -> Unit,
    val onDayStep: (later: Boolean) -> Unit,
)

/**
 * The monthly reminder: whether MilO reminds Shawn that last month's report has not been sent,
 * and from which day of the month. Switched off, it shows nothing else, like a day of the work
 * schedule: the day is kept, and comes back with the switch.
 *
 * The card says what the reminder goes by, because it is not an alarm for one moment: it comes
 * back every day until the report is recorded as sent.
 */
@Composable
internal fun ReminderCard(state: SettingsUiState.Ready, actions: ReminderActions) {
    SectionCard(title = stringResource(R.string.settings_reminder_title)) {
        SwitchRow(
            label = stringResource(R.string.settings_reminder_enabled),
            checked = state.reminderEnabled,
            onCheckedChange = actions.onEnabled,
        )
        Quiet(stringResource(R.string.settings_reminder_detail))
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
                supportingText = stringResource(R.string.settings_reminder_day_detail),
            )
        }
    }
}
