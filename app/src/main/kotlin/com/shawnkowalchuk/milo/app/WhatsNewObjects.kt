package com.shawnkowalchuk.milo.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.shawnkowalchuk.milo.data.changelog.CHANGELOG_ASSET
import com.shawnkowalchuk.milo.data.changelog.ChangelogSource
import com.shawnkowalchuk.milo.feature.whatsnew.VersionTile
import com.shawnkowalchuk.milo.feature.whatsnew.WhatsNewNoticeViewModel
import com.shawnkowalchuk.milo.feature.whatsnew.WhatsNewScreen
import com.shawnkowalchuk.milo.feature.whatsnew.WhatsNewViewModel
import com.shawnkowalchuk.milo.platform.system.InstalledVersion
import com.shawnkowalchuk.milo.platform.system.readInstalledVersion

/**
 * The version on this phone and the list of changes built into the app (2026-10-08), for the
 * Version tile on Settings and the What's new screen.
 *
 * Like [WidgetObjects], it is a part of the [AppContainer] (`container.whatsNew`), a class of
 * its own only because the container is at its size limit.
 */
class WhatsNewObjects(private val appContext: Context) {
    /** Read once a process: it changes only with an update, which starts a new process. */
    val installedVersion: InstalledVersion by lazy { readInstalledVersion(appContext) }

    val changelog: ChangelogSource by lazy {
        ChangelogSource(open = { appContext.assets.open(CHANGELOG_ASSET) })
    }
}

/**
 * The What's new screen, for [MiloNavigation], which is at its size limit (ENGINEERING_STANDARDS
 * section 3).
 */
@Composable
internal fun WhatsNewEntry(container: AppContainer, backStack: NavBackStack<NavKey>) {
    WhatsNewScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        WhatsNewViewModel(
                            changelog = container.whatsNew.changelog::read,
                            installed = container.whatsNew.installedVersion,
                            eventLog = container.eventLogRepository,
                            clock = System::currentTimeMillis,
                        )
                    }
                },
            ),
        onBack = { backStack.closeIfOnTop(WhatsNewKey) },
    )
}

/** The Version tile at the end of Settings, which opens the What's new screen. */
@Composable
internal fun VersionTileEntry(container: AppContainer, backStack: NavBackStack<NavKey>) {
    VersionTile(
        installed = container.whatsNew.installedVersion,
        onOpen = { backStack.openOnTop(WhatsNewKey) },
    )
}

/** How [MiloApp] learns that the What's new screen is to open by itself, after an update. */
internal fun whatsNewNoticeViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            WhatsNewNoticeViewModel(
                settings = container.settingsStore,
                installedVersion = container.whatsNew.installedVersion.name,
                eventLog = container.eventLogRepository,
                clock = System::currentTimeMillis,
            )
        }
    }
