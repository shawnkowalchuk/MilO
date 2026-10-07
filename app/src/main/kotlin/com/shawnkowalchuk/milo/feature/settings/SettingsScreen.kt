package com.shawnkowalchuk.milo.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.ScreenTitle
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import java.time.DayOfWeek
import java.time.LocalTime

/** The kind of file the picker offers for the trip-start sound. */
private const val ANY_AUDIO = "audio/*"

/** What the cards of the Settings screen can ask for. */
internal class SettingsActions(
    val onChangeTruck: () -> Unit,
    val onGraceStep: (longer: Boolean) -> Unit,
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

/** What the card of the report for the accountant can ask for: each field reports its text. */
internal class ReportDetailActions(
    val onName: (String) -> Unit,
    val onCompany: (String) -> Unit,
    val onVehicle: (String) -> Unit,
    val onAccountantEmail: (String) -> Unit,
)

/**
 * What the two cards of the work schedule can ask for.
 *
 * @param onDayStart and [onDayEnd] carry the time picker's answer: an hour from 0 to 23 and a
 * minute.
 */
internal class ScheduleActions(
    val onDayTracked: (DayOfWeek, Boolean) -> Unit,
    val onDayStart: (DayOfWeek, hour: Int, minute: Int) -> Unit,
    val onDayEnd: (DayOfWeek, hour: Int, minute: Int) -> Unit,
    val onCopyHours: (DayOfWeek) -> Unit,
    val onIgnoreOutside: (Boolean) -> Unit,
)

/**
 * The settings: which truck, who the report for the accountant is from and where it goes, how
 * long a trip waits for the truck to reconnect, how short a trip may be, the work schedule that
 * makes a trip Business or Personal, what becomes of a trip outside it, the driving alert, the
 * daily check that a work day has a trip, the monthly reminder to send last month's report,
 * the sound of a trip start, and, last, Android's backup with the export and import of all data.
 *
 * @param dataViewModel the last card's own ViewModel: see [DataViewModel].
 * @param checkViewModel the daily check's card has one of its own too.
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
        checkCard = { NothingRecordedCard(checkViewModel) },
        dataCard = { DataCard(dataViewModel) },
    )
}

/**
 * @param checkCard the card of the daily check, handed in whole because it has a state of its
 * own. It stands with the driving alert: both speak up when a trip is not being recorded.
 * @param dataCard the card for backup, export and import. It is handed in whole, because it
 * has a state of its own: it is shown also when the settings cannot be read, which is when a
 * copy of the trips is wanted most.
 */
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    checkCard: @Composable () -> Unit,
    dataCard: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
    ) {
        ScreenTitle(text = stringResource(R.string.settings_title), onBack = onBack)
        when (state) {
            SettingsUiState.Reading ->
                Text(
                    text = stringResource(R.string.settings_reading),
                    style = MaterialTheme.typography.bodyLarge,
                )

            SettingsUiState.Unreadable ->
                StatusRow(
                    label = stringResource(R.string.settings_unreadable),
                    status = RowStatus.PROBLEM,
                )

            is SettingsUiState.Ready -> {
                if (state.problem == SettingsProblem.COULD_NOT_SAVE) {
                    StatusRow(
                        label = stringResource(R.string.settings_could_not_save),
                        status = RowStatus.PROBLEM,
                    )
                }
                TruckCard(state, actions)
                // Second, and not last: the Report screen sends Shawn here for his name and
                // the accountant's address, and the long schedule card would bury them.
                ReportDetailsCard(state.report, actions.report)
                // Under the report's own card: it is the reminder to send that report.
                ReminderCard(state, actions.reminder)
                TripRulesCard(state, actions)
                ScheduleCard(state, actions.schedule)
                OutsideScheduleCard(state, actions.schedule)
                DrivingAlertCard(state, actions)
                checkCard()
                SoundCard(state, actions)
            }
        }
        // Last: it is used a few times a year, and it is the one card that can replace
        // everything, so it is not among the settings that are changed in passing.
        if (state != SettingsUiState.Reading) dataCard()
    }
}

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun SettingsPreview() {
    val state =
        SettingsUiState.Ready(
            truckPaired = true,
            truckName = "Work truck",
            gracePeriodSeconds = 150,
            canShortenGrace = true,
            canLengthenGrace = true,
            minimumDistanceMetres = 300,
            canLowerMinimum = true,
            canRaiseMinimum = true,
            soundEnabled = true,
            usesOwnSound = true,
            ownSoundName = "r2d2.mp3",
            copyingSound = false,
            schedule =
                DayOfWeek.entries.map { day ->
                    ScheduleDay(
                        day = day,
                        tracked = day < DayOfWeek.SATURDAY,
                        start = LocalTime.of(if (day == DayOfWeek.FRIDAY) 7 else 8, 0),
                        end = LocalTime.of(16, 30),
                        canCopy = day < DayOfWeek.SATURDAY,
                        hoursRefused = false,
                    )
                },
            ignoreOutsideSchedule = false,
            drivingAlertEnabled = true,
            report =
                ReportFields(
                    name = "Sam Driver",
                    company = "",
                    vehicle = "Ford F-150, plate ABC-123",
                    accountantEmail = "accounts@example",
                    emailRefused = true,
                ),
            reminderEnabled = true,
            reminderDay = 1,
            canRemindEarlier = false,
            canRemindLater = true,
            problem = SettingsProblem.SOUND_NOT_PLAYABLE,
        )
    val schedule = ScheduleActions({ _, _ -> }, { _, _, _ -> }, { _, _, _ -> }, {}, {})
    val report = ReportDetailActions({}, {}, {}, {})
    val reminder = ReminderActions({}, {})
    MiloTheme {
        Surface {
            SettingsContent(
                state = state,
                actions =
                    SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, schedule, report, reminder),
                onBack = {},
                checkCard = {},
                dataCard = {},
            )
        }
    }
}
