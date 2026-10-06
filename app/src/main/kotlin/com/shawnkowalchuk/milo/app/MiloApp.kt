package com.shawnkowalchuk.milo.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.rememberNavBackStack
import com.shawnkowalchuk.milo.core.designsystem.component.MiloNavigationBar
import com.shawnkowalchuk.milo.core.designsystem.component.NavigationBarEntry
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The root of the phone UI: applies the theme once, and frames every screen with the bottom
 * navigation bar. Which screen is showing is decided by the back stack, which lives here and is
 * handed to [MiloNavigation].
 */
@Composable
fun MiloApp(container: AppContainer) {
    MiloTheme {
        // Saved and restored by Navigation 3, so the screen that was showing comes back after
        // Android has put MilO away and brought it back.
        val backStack = rememberNavBackStack(HomeKey)
        val showing = topLevelOf(backStack)

        // The app draws behind the status and navigation bars (see MainActivity). Scaffold
        // reports how much room those bars and the bottom bar take, so the content starts and
        // ends clear of them.
        Scaffold(
            bottomBar = {
                MiloNavigationBar(
                    entries =
                        TopLevelDestination.entries.map { destination ->
                            NavigationBarEntry(
                                label = stringResource(destination.labelRes),
                                icon = destination.icon,
                                selected = destination == showing,
                                onClick = { backStack.showTopLevel(destination.key, HomeKey) },
                            )
                        },
                )
            },
        ) { innerPadding ->
            MiloNavigation(
                container = container,
                backStack = backStack,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
