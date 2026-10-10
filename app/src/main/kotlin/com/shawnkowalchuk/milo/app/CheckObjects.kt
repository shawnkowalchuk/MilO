package com.shawnkowalchuk.milo.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.feature.settings.NothingRecordedViewModel
import com.shawnkowalchuk.milo.platform.nothingrecorded.AlarmManagerNothingRecordedAlarm
import com.shawnkowalchuk.milo.platform.nothingrecorded.NothingRecordedAlarm
import com.shawnkowalchuk.milo.platform.nothingrecorded.NothingRecordedCheck
import com.shawnkowalchuk.milo.platform.nothingrecorded.NothingRecordedNotification
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * The long-lived object around the daily check that MilO is still recording trips: the
 * "nothing recorded" check, which says so when a work day has no trip by a set time.
 *
 * Like [ReportObjects], [TransferObjects] and [CarObjects], it is a part of the [AppContainer],
 * which creates it once and through which everything reaches it (`container.checks`); it is a
 * class of its own only because the container is at its size limit, and it follows the
 * container's rules.
 *
 * @param container where the storage this object reads is made.
 */
class CheckObjects(private val appContext: Context, private val container: AppContainer) {
    /**
     * Asks, once a day and whenever MilO runs anyway, whether a trip has been recorded today,
     * and posts a notification if a work day has none. It is handed the settings, two reads of
     * the stored trips, what the trip controller publishes and the controller's "tell me when
     * you have caught up", and neither the controller nor a way to write a trip: it cannot
     * start, end or change one.
     */
    val nothingRecorded: NothingRecordedCheck by lazy {
        // Both are made on first use, which is on a background thread, the first time the check
        // looks: building the check at process start then asks nothing of Android on the main
        // thread, where a trip trigger may be waiting. MilO's one activity is named here and
        // handed in, so that the check's package does not import `app/` for it.
        val notification by lazy {
            NothingRecordedNotification(appContext, opens = MainActivity::class.java)
        }
        val alarm by lazy { AlarmManagerNothingRecordedAlarm(appContext) }
        NothingRecordedCheck(
            alarm =
                object : NothingRecordedAlarm {
                    override fun setFor(atMs: Long) = alarm.setFor(atMs)

                    override fun setAfter(delayMs: Long) = alarm.setAfter(delayMs)

                    override fun cancel() = alarm.cancel()
                },
            show = { notification.show() },
            withdraw = { notification.cancel() },
            settings = container.settingsStore,
            tripsStartedBetween = { fromMs, untilMs ->
                container.tripRepository.observeTripsStartedBetween(fromMs, untilMs).first()
            },
            openTrip = container.tripRepository::findOpenTrip,
            tripActivity = container.tripController.activity,
            whenTripsCaughtUp = container.tripController::whenCaughtUp,
            eventLog = container.eventLogRepository,
            crashFileStore = container.crashFileStore,
            clock = container.clock,
            phoneClockAgrees = container.clocks.agreesWithPhone,
            clockOnProbation = container.clocks.onProbation,
            zone = ZoneId::systemDefault,
            scope = container.applicationScope,
        )
    }
}

/**
 * How the Settings screen's card for the "nothing recorded" check gets its ViewModel. It stands
 * here because [MiloNavigation] is at its size limit (ENGINEERING_STANDARDS section 3).
 */
internal fun nothingRecordedViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            NothingRecordedViewModel(
                settings = container.settingsStore,
                armCheck = container.checks.nothingRecorded::arm,
                eventLog = container.eventLogRepository,
                clock = container.clock,
            )
        }
    }
