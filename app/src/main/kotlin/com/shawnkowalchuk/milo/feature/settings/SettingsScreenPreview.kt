package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.shawnkowalchuk.milo.core.allowance.DEFAULT_CENTS_PER_KM
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.withHoursOnEveryDay
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.LastExport
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.transfer.PointsTaken
import com.shawnkowalchuk.milo.platform.transfer.TransferWork
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

// The Settings screen as Android Studio draws it, in the states an emulator cannot be brought
// into by hand (a paired truck, a sound of Shawn's own, an import that was made). In a file of
// its own to keep the screen's file under the size limit (ENGINEERING_STANDARDS section 3).
//
// Sample values are written inline because a preview is never shown to a user or shipped.

/**
 * One state of the whole screen.
 *
 * @param check the daily check's tile, or null while its settings have not been read.
 */
internal class SettingsSample(
    val screen: SettingsUiState,
    val check: NothingRecordedCardState?,
    val data: DataCardState,
)

private val noon: LocalTime = LocalTime.of(12, 0)

private fun ready(
    settings: MiloSettings,
    problem: SettingsProblem? = null,
    problemDay: DayOfWeek? = null,
    copyingSound: Boolean = false,
    refusedEmail: String? = null,
) = settingsUiState(settings, copyingSound, problem, problemDay, refusedEmail)

/** The states, in the order an emulator is asked for them by number. */
internal class SettingsSamples : PreviewParameterProvider<SettingsSample> {
    override val values: Sequence<SettingsSample> =
        sequenceOf(
            // 0: as the design draws it. Nothing paired, nothing typed, nothing exported.
            SettingsSample(
                screen = ready(MiloSettings()),
                check = NothingRecordedCardState(
                    enabled = true,
                    checkAt = noon,
                    couldNotSave = false,
                ),
                data = DataCardState(),
            ),
            // 1: in use. A truck, the report's details, a sound of his own, a Friday that
            // ends early, an export and an import behind him.
            SettingsSample(
                screen =
                    ready(
                        MiloSettings(
                            truckAddress = "00:11:22:33:44:55",
                            truckName = "Work truck",
                            customSoundUri = "file:///sound",
                            customSoundName = "r2d2.mp3",
                            schedule =
                                DEFAULT_WORK_SCHEDULE.withEnd(DayOfWeek.FRIDAY, LocalTime.of(13, 0))
                                    ?: DEFAULT_WORK_SCHEDULE,
                            ignoreTripsOutsideSchedule = true,
                            reportName = "Sam Driver",
                            reportVehicle = "Ford F-150, plate ABC-123",
                            reminderDay = 22,
                        ),
                        refusedEmail = "accounts@example",
                    ),
                check = NothingRecordedCardState(
                    enabled = true,
                    checkAt = noon,
                    couldNotSave = false,
                ),
                data =
                    DataCardState(
                        lastExport = LastExport(atMs = 1_791_234_567_890, withPoints = true),
                        safetyCopiesAtMs = listOf(1_791_234_000_000),
                        lines =
                            listOf(
                                OutcomeLine.Imported(1_234, 12, 380_112, PointsTaken.TAKEN),
                                OutcomeLine.PairTruckAgain,
                            ),
                    ),
            ),
            // 2: the edges. Hours outside the drawn line and a time that was refused, a file
            // that was refused, a change that could not be stored, an export under way.
            SettingsSample(
                screen =
                    ready(
                        MiloSettings(
                            schedule =
                                DEFAULT_WORK_SCHEDULE.withHoursOnEveryDay(
                                    LocalTime.of(3, 30),
                                    LocalTime.of(23, 59),
                                ) ?: DEFAULT_WORK_SCHEDULE,
                            gracePeriodSeconds = 30,
                            minimumTripDistanceMetres = 2_000,
                        ),
                        problem = SettingsProblem.HOURS_END_NOT_AFTER_START,
                    ),
                check = NothingRecordedCardState(
                    enabled = true,
                    checkAt = noon,
                    couldNotSave = true,
                ),
                data = DataCardState(working = TransferWork.EXPORTING),
            ),
            // 3: switched off. No work day, no reminder, no check, no sound, and a file that
            // is being copied.
            SettingsSample(
                screen =
                    ready(
                        MiloSettings(
                            schedule =
                                DayOfWeek.entries.fold(DEFAULT_WORK_SCHEDULE) { schedule, day ->
                                    schedule.withTracked(day, false)
                                },
                            soundEnabled = false,
                            drivingAlertEnabled = false,
                            reminderEnabled = false,
                        ),
                        problem = SettingsProblem.SOUND_TOO_LARGE,
                        copyingSound = true,
                    ),
                check = NothingRecordedCardState(
                    enabled = false,
                    checkAt = noon,
                    couldNotSave = false,
                ),
                data = DataCardState(tripInProgress = true),
            ),
        )
}

/** The odometer as the previews show it: a reading of a few days before, and trips since. */
private val PREVIEW_ODOMETER =
    OdometerCardState(
        figure =
            OdometerFigure(
                value = 123_802,
                unit = DistanceUnit.KILOMETRES,
                reading =
                    OdometerReading(
                        atMs = 1_791_000_000_000,
                        value = 123_456,
                        unit = DistanceUnit.KILOMETRES,
                    ),
                drivenTenths = 3_462,
                estimated = true,
            ),
        zone = ZoneId.of("America/Edmonton"),
        couldNotSave = false,
        unit = DistanceUnit.KILOMETRES,
    )

@Preview
@Composable
private fun SettingsPreview(@PreviewParameter(SettingsSamples::class) sample: SettingsSample) {
    val schedule = ScheduleActions({ _, _ -> }, { _, _, _ -> }, { _, _, _ -> }, {}, {})
    val report = ReportDetailActions({}, {}, {}, {})
    val reminder = ReminderActions({}, {})
    MiloTheme {
        Surface {
            SettingsContent(
                state = sample.screen,
                actions =
                    SettingsActions(
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        schedule,
                        report,
                        reminder,
                    ),
                onBack = null,
                setupTile = {},
                checkTile = {
                    sample.check?.let { NothingRecordedTileContent(it, {}, { _, _ -> }) }
                },
                odometerTile = { OdometerTileContent(PREVIEW_ODOMETER) { _, _ -> true } },
                widgetTile = {
                    HomeWidgetTileContent(
                        HomeWidgetCardState(
                            enabled = true,
                            canAskToAdd = true,
                            centsPerKm = DEFAULT_CENTS_PER_KM,
                            couldNotSave = false,
                        ),
                        {},
                        { true },
                        {},
                    )
                },
                dataTile = {
                    DataTileContent(sample.data, DataActions({}, {}, {}, {}, {}, {}))
                },
            )
        }
    }
}
