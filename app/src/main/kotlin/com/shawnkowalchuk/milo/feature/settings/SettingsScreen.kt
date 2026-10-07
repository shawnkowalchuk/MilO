package com.shawnkowalchuk.milo.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import java.time.DayOfWeek

/** The kind of file the picker offers for the trip-start sound. */
private const val ANY_AUDIO = "audio/*"

/** What the tiles of the Settings screen can ask for. */
internal class SettingsActions(
    val onChangeTruck: () -> Unit,
    val onGraceStep: (longer: Boolean) -> Unit,
    val onParkedLimitStep: (longer: Boolean) -> Unit,
    val onMinimumDistanceStep: (longer: Boolean) -> Unit,
    val onSoundEnabled: (Boolean) -> Unit,
    val onPlaySound: () -> Unit,
    val onPickOwnSound: () -> Unit,
    val onUseBuiltInSound: () -> Unit,
    val onDrivingAlertEnabled: (Boolean) -> Unit,
    val schedule: ScheduleActions,
    val report: ReportDetailActions,
    val reminder: ReminderActions,
)

/** What the tile of the report for the accountant can ask for: each field reports its text. */
internal class ReportDetailActions(
    val onName: (String) -> Unit,
    val onCompany: (String) -> Unit,
    val onVehicle: (String) -> Unit,
    val onAccountantEmail: (String) -> Unit,
)

/**
 * What the two tiles of the work schedule can ask for.
 *
 * @param onDayStart and [onDayEnd] carry a new time from a slider or from the time picker: an
 * hour from 0 to 23 and a minute, for one day, or for every day at once if the day is null.
 */
internal class ScheduleActions(
    val onDayTracked: (DayOfWeek, Boolean) -> Unit,
    val onDayStart: (DayOfWeek?, hour: Int, minute: Int) -> Unit,
    val onDayEnd: (DayOfWeek?, hour: Int, minute: Int) -> Unit,
    val onCopyHours: (DayOfWeek) -> Unit,
    val onIgnoreOutside: (Boolean) -> Unit,
)

/**
 * The settings, laid out as the owner's design draws them: which truck, beside the driving
 * alert; who the report for the accountant is from and where it goes; the monthly reminder to
 * send last month's report; how long a trip waits for the truck to reconnect, how long the
 * truck may stand still before a trip ends and how short a trip may be; the work schedule that
 * makes a trip Business or Personal; what becomes of a trip outside it; the daily check that a
 * work day has a trip; the sound of a trip start; and, last, Android's backup with the export
 * and import of all data.
 *
 * @param dataViewModel the last tile's own ViewModel: see [DataViewModel].
 * @param checkViewModel the daily check's tile has one of its own too.
 * @param onChangeTruck opens the truck pairing screen. Navigation belongs to the app, not the
 * feature.
 * @param onBack leaves the screen.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    dataViewModel: DataViewModel,
    checkViewModel: NothingRecordedViewModel,
    onChangeTruck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // Android's own file picker. It answers with the file Shawn chose, or with nothing if he
    // closed it, in which case nothing changes.
    val soundPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked != null) viewModel.onOwnSoundPicked(picked.toString())
        }

    val actions =
        SettingsActions(
            onChangeTruck = onChangeTruck,
            onGraceStep = viewModel::onGraceStep,
            onParkedLimitStep = viewModel::onParkedLimitStep,
            onMinimumDistanceStep = viewModel::onMinimumDistanceStep,
            onSoundEnabled = viewModel::onSoundEnabled,
            onPlaySound = viewModel::onPlaySound,
            onPickOwnSound = {
                try {
                    soundPicker.launch(arrayOf(ANY_AUDIO))
                } catch (noPicker: ActivityNotFoundException) {
                    // A phone with its file picker removed or disabled. The screen says so.
                    viewModel.onNoFilePicker()
                }
            },
            onUseBuiltInSound = viewModel::onUseBuiltInSound,
            onDrivingAlertEnabled = viewModel::onDrivingAlertEnabled,
            schedule =
                ScheduleActions(
                    onDayTracked = viewModel::onDayTracked,
                    onDayStart = viewModel::onDayStart,
                    onDayEnd = viewModel::onDayEnd,
                    onCopyHours = viewModel::onCopyHours,
                    onIgnoreOutside = viewModel::onIgnoreOutsideSchedule,
                ),
            report =
                ReportDetailActions(
                    onName = viewModel::onReportName,
                    onCompany = viewModel::onReportCompany,
                    onVehicle = viewModel::onReportVehicle,
                    onAccountantEmail = viewModel::onAccountantEmail,
                ),
            reminder =
                ReminderActions(
                    onEnabled = viewModel::onReminderEnabled,
                    onDayStep = viewModel::onReminderDayStep,
                ),
        )
    SettingsContent(
        state = state,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
        checkTile = { NothingRecordedTile(checkViewModel) },
        dataTile = { DataTile(dataViewModel) },
    )
}

/**
 * The screen's tiles, top to bottom in the design's order. The two tiles the design does not
 * draw stand where they belong: the daily check under the work schedule it goes by, and the
 * tile for backup, export and import last.
 *
 * @param checkTile the tile of the daily check, handed in whole because it has a state of its
 * own.
 * @param dataTile the tile for backup, export and import. It is handed in whole, because it
 * has a state of its own: it is shown also when the settings cannot be read, which is when a
 * copy of the trips is wanted most.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    checkTile: @Composable () -> Unit,
    dataTile: @Composable () -> Unit,
) {
    val spacing = MiloTheme.spacing
    TileColumn(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(top = spacing.tileGap, bottom = spacing.small),
    ) {
        ScreenTitle(
            text = stringResource(R.string.settings_title),
            // With the gap between two tiles, the design's 16 under the title.
            modifier = Modifier.padding(bottom = spacing.buttonGap),
            onBack = onBack,
        )
        when (state) {
            SettingsUiState.Reading -> NoteTile { Note(stringResource(R.string.settings_reading)) }

            SettingsUiState.Unreadable ->
                NoteTile {
                    StatusRow(
                        label = stringResource(R.string.settings_unreadable),
                        status = RowStatus.PROBLEM,
                    )
                }

            is SettingsUiState.Ready -> {
                if (state.problem == SettingsProblem.COULD_NOT_SAVE) {
                    NoteTile {
                        StatusRow(
                            label = stringResource(R.string.settings_could_not_save),
                            status = RowStatus.PROBLEM,
                        )
                    }
                }
                TruckAndAlertTiles(state, actions)
                // Second, and not last: the Report screen sends Shawn here for his name and
                // the accountant's address.
                ReportDetailsTile(state.report, actions.report)
                // Under the report's own tile: it is the reminder to send that report.
                ReminderTile(state, actions.reminder)
                TripRulesTile(state, actions)
                ScheduleTile(state, actions.schedule)
                OutsideHoursTile(state, actions.schedule)
                // Under the schedule: the check goes by its work days and their start.
                checkTile()
                SoundTile(state, actions)
            }
        }
        // Last: it is used a few times a year, and it is the one tile that can replace
        // everything, so it is not among the settings that are changed in passing.
        if (state != SettingsUiState.Reading) dataTile()
    }
}

/** A tile for one thing the screen has to say in place of, or above, its settings. */
@Composable
private fun NoteTile(content: @Composable () -> Unit) {
    Tile(modifier = Modifier.fillMaxWidth(), padding = TilePadding.EVEN) { content() }
}
