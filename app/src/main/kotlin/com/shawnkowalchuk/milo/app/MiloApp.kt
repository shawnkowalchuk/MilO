package com.shawnkowalchuk.milo.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.feature.home.HomeScreen

/**
 * The root of the phone UI: applies the theme once and decides which screen is showing.
 *
 * There is a single screen today, so it is placed directly. When the second screen arrives this
 * is where the navigation host goes, and it stays the only place that knows about more than one
 * feature: features never navigate to, or import from, each other.
 */
@Composable
fun MiloApp() {
    MiloTheme {
        // The app draws behind the status and navigation bars (see MainActivity). Scaffold
        // reports how much room those bars take so the content starts and ends clear of them.
        Scaffold { innerPadding ->
            HomeScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
