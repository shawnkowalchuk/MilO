package com.shawnkowalchuk.milo.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.feature.settings.HomeWidgetViewModel
import com.shawnkowalchuk.milo.platform.widget.HomeWidget
import java.time.ZoneId

/**
 * The long-lived object around the home-screen widget (2026-10-07): the one that keeps every
 * widget on the home screen in step with the trip, applies the switch in Settings, and asks the
 * home screen to add one.
 *
 * Like [CarObjects] and [CheckObjects], it is a part of the [AppContainer], which creates it
 * once and through which everything reaches it (`container.widgets`); it is a class of its own
 * only because the container is at its size limit, and it follows the container's rules.
 */
class WidgetObjects(private val appContext: Context, private val container: AppContainer) {
    /**
     * It is handed what the trip controller publishes and read access to the trips, and neither
     * the controller nor a way to write a trip: its buttons are PendingIntents that reach the
     * controller the way every other button does.
     */
    val homeWidget: HomeWidget by lazy {
        HomeWidget(
            context = appContext,
            activity = container.tripController.activity,
            trips = container.tripRepository,
            settings = container.settingsStore,
            eventLog = container.eventLogRepository,
            clock = System::currentTimeMillis,
            zone = ZoneId::systemDefault,
            scope = container.applicationScope,
        )
    }
}

/**
 * How the Settings screen's widget tile gets its ViewModel. It stands here because
 * [MiloNavigation] is at its size limit (ENGINEERING_STANDARDS section 3).
 */
internal fun homeWidgetViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val widget = container.widgets.homeWidget
            HomeWidgetViewModel(
                settings = container.settingsStore,
                applySwitch = widget::applySwitch,
                homeScreenTakesRequests = widget::homeScreenTakesRequests,
                askToAdd = widget::askToAdd,
                eventLog = container.eventLogRepository,
                clock = System::currentTimeMillis,
            )
        }
    }
