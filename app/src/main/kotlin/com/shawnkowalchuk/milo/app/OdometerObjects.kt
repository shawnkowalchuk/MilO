package com.shawnkowalchuk.milo.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.feature.settings.OdometerViewModel
import java.time.ZoneId

/**
 * How the Settings screen's odometer tile gets its ViewModel. It stands here because
 * [MiloNavigation] is at its size limit (ENGINEERING_STANDARDS section 3), like the factories
 * of the other tiles with a ViewModel of their own.
 */
internal fun odometerViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            OdometerViewModel(
                settings = container.settingsStore,
                trips = container.tripRepository,
                activity = container.tripController.activity,
                eventLog = container.eventLogRepository,
                clock = System::currentTimeMillis,
                zone = ZoneId::systemDefault,
            )
        }
    }
