package com.shawnkowalchuk.milo.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.PrimaryButton
import com.shawnkowalchuk.milo.core.designsystem.component.SectionCard
import com.shawnkowalchuk.milo.core.designsystem.component.StatusRow
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.util.formatKilometres

// Nothing records trips yet, so today's total is a fixed zero. Phase 1 replaces it with state
// from a HomeViewModel; this screen must not grow logic of its own in the meantime.
private const val PLACEHOLDER_TODAY_METRES = 0.0

/**
 * Placeholder home screen. It exists to prove the design system on the real phone: it is built
 * only from the shared components and tokens, in the layout the real home screen will have
 * (today's total, a status section, one primary action).
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // Large font settings or a small window must scroll rather than cut content off.
                .verticalScroll(rememberScrollState())
                .padding(MiloTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        SectionCard(title = stringResource(R.string.home_today_title)) {
            Text(
                text =
                    stringResource(
                        R.string.distance_km,
                        formatKilometres(PLACEHOLDER_TODAY_METRES, locale),
                    ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.home_today_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.home_status_title)) {
            StatusRow(
                label = stringResource(R.string.home_status_installed),
                isOk = true,
                supportingText = stringResource(R.string.home_status_installed_detail),
            )
            StatusRow(
                label = stringResource(R.string.home_status_detection),
                isOk = false,
                supportingText = stringResource(R.string.home_status_detection_detail),
            )
        }

        // Disabled rather than wired to a handler that does nothing: a button that can be pressed
        // must do what it says, and starting a trip does not exist until phase 1.
        PrimaryButton(
            text = stringResource(R.string.home_start_trip),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    MiloTheme {
        Surface {
            HomeScreen()
        }
    }
}
