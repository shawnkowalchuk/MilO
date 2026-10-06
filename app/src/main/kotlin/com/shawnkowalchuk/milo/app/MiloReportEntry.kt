package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.shawnkowalchuk.milo.feature.report.ReportReading
import com.shawnkowalchuk.milo.feature.report.ReportRecords
import com.shawnkowalchuk.milo.feature.report.ReportScreen
import com.shawnkowalchuk.milo.feature.report.ReportViewModel
import java.time.YearMonth
import java.time.ZoneId

/**
 * The Report screen as an entry of the back stack: its ViewModel is given what it needs from
 * the [AppContainer], and its two ways out are carried out here. It is one of the entries of
 * [MiloNavigation], kept in a file of its own because that function is at its size limit
 * (ENGINEERING_STANDARDS section 3).
 *
 * Back leads to Trips, where the screen was opened. "Open Settings" puts Settings on top, so
 * that Back from there leads back to the report that was being made.
 */
@Composable
internal fun ReportEntry(container: AppContainer, backStack: NavBackStack<NavKey>, key: ReportKey) {
    ReportScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        val records =
                            ReportRecords(
                                sent = container.sentReportRepository,
                                settings = container.settingsStore,
                                eventLog = container.eventLogRepository,
                                clock = System::currentTimeMillis,
                            )
                        ReportViewModel(
                            openedFor = YearMonth.of(key.year, key.month),
                            reading =
                                ReportReading(
                                    trips = container.tripRepository,
                                    sentReports = container.sentReportRepository,
                                    settings = container.settingsStore,
                                    records = records,
                                ),
                            documents = container.reportDocuments,
                            handOff = container.reportHandOff,
                            texts = container.reportTexts,
                            records = records,
                            clock = System::currentTimeMillis,
                            zone = ZoneId::systemDefault,
                        )
                    }
                },
            ),
        onBack = { backStack.closeIfOnTop(key) },
        onOpenSettings = { backStack.openOnTop(SettingsKey) },
    )
}
