package com.shawnkowalchuk.milo.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.AppHeader
import com.shawnkowalchuk.milo.core.designsystem.component.PrimaryButton
import com.shawnkowalchuk.milo.core.designsystem.component.Tile
import com.shawnkowalchuk.milo.core.designsystem.component.TileColumn
import com.shawnkowalchuk.milo.core.designsystem.component.TileKind
import com.shawnkowalchuk.milo.core.designsystem.component.TilePadding
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** The tiles under the first one, in order: each a title and what it says. */
private val SECTIONS =
    listOf(
        R.string.onboarding_trips_title to R.string.onboarding_trips_text,
        R.string.onboarding_sorting_title to R.string.onboarding_sorting_text,
        R.string.onboarding_report_title to R.string.onboarding_report_text,
        R.string.onboarding_privacy_title to R.string.onboarding_privacy_text,
        R.string.onboarding_next_title to R.string.onboarding_next_text,
    )

/**
 * The first page of the first start (Shawn's request of 2026-10-08: "What the app does and how
 * it works. than a ok."), shown once, before any other screen and without the bottom bar.
 *
 * One page that scrolls (Shawn's choice of "One page, then OK" over a few pages), made of the
 * app's own parts: the top line with the app's mark, the one accent tile of the screen with
 * what MilO is, a plain tile for each thing to know, and the main button at the end. Nothing was
 * added to the design system for it.
 *
 * Back leaves MilO, as from Home; the page shows again at the next start until OK is pressed.
 *
 * @param onOk the button at the end. The app stores the stage and opens Setup.
 */
@Composable
fun OnboardingScreen(onOk: () -> Unit, modifier: Modifier = Modifier) {
    // The bottom bar's frame is not around this page, so it gives itself the page's colour and
    // its words' colour, and keeps clear of the status and navigation bars, as that frame does.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        OnboardingContent(onOk)
    }
}

@Composable
private fun OnboardingContent(onOk: () -> Unit) {
    val spacing = MiloTheme.spacing
    TileColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                // A large font or a small window scrolls rather than cutting the button off.
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.small),
    ) {
        AppHeader(
            title = stringResource(R.string.onboarding_title),
            modifier = Modifier.padding(bottom = spacing.extraSmall),
        )
        Tile(
            modifier = Modifier.fillMaxWidth(),
            kind = TileKind.ACCENT,
            padding = TilePadding.ROOMY,
            gap = spacing.small,
        ) {
            Text(
                text = stringResource(R.string.onboarding_hero),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.onboarding_hero_line),
                style = MiloTheme.textStyles.sentence,
            )
        }
        for ((title, text) in SECTIONS) Section(title, text)
        PrimaryButton(
            text = stringResource(R.string.action_ok),
            onClick = onOk,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One thing to know: its title, and a few sentences under it in the quieter colour. */
@Composable
private fun Section(@StringRes title: Int, @StringRes text: Int) {
    Tile(
        modifier = Modifier.fillMaxWidth(),
        padding = TilePadding.EVEN,
        gap = MiloTheme.spacing.small,
    ) {
        Text(
            text = stringResource(title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(text),
            style = MiloTheme.textStyles.sentence,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun OnboardingPreview() {
    MiloTheme { OnboardingScreen(onOk = {}) }
}
