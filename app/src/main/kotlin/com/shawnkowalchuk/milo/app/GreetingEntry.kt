package com.shawnkowalchuk.milo.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shawnkowalchuk.milo.feature.greeting.GreetingViewModel

/**
 * How [MiloApp] gets the greeting's ViewModel (2026-10-09). It stands here, beside the other
 * factories that are not [MiloNavigation]'s, because that file is at its size limit
 * (ENGINEERING_STANDARDS section 3).
 */
internal fun greetingViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            GreetingViewModel(
                settings = container.settingsStore,
                eventLog = container.eventLogRepository,
                clock = container.clock,
            )
        }
    }
